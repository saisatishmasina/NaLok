import { useEffect, useRef, useState } from 'react'
import type { DependencyList } from 'react'
import type { StudySession } from '../api/types'

export interface ResourceState<T> {
  data: T | null
  error: string | null
  loading: boolean
}

function errorText(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Unexpected error'
}

function sameDeps(one: DependencyList, other: DependencyList): boolean {
  return one.length === other.length && one.every((value, index) => value === other[index])
}

/**
 * Coordination state for ONE resource identity (one deps value). Requests,
 * deferred refresh intents and the latest-issued token all live HERE, not in
 * the effect closure — so an older generation's draining load can never
 * clear a NEWER generation's queued intent, and navigating away (deps
 * change) orphans the whole object cleanly.
 */
interface ResourceControl {
  /** Loads in flight for this resource right now (any generation). */
  busy: number
  /** A refetch intent queued while busy; run once when busy reaches 0. */
  pending: boolean
  /**
   * Monotonic request token. Only the response of the LATEST ISSUED load may
   * write state — a slow older response (any generation, including Strict
   * Mode double-effects) can never overwrite a newer value.
   */
  request: number
}

/**
 * Fetch-once-per-deps resource state with an alive-guard so a slow response
 * of a discarded render pass can never overwrite a newer one.
 *
 * `pollMs > 0` keeps the resource FRESH WITHOUT RELOADS: the loader re-runs
 * on an interval inside the SAME effect (no extra fetch-in-effect sites).
 *
 * A failed load keeps the previous data (the note surfaces as the error), so
 * one transient network hiccup cannot blank a card.
 */
export function useResource<T>(
  loader: () => Promise<T>,
  deps: DependencyList,
  pollMs = 0,
): ResourceState<T> & { refetch: () => void } {
  const [state, setState] = useState<ResourceState<T>>({
    data: null,
    error: null,
    loading: true,
  })
  // Bump-only handle: a click can force an immediate reload without knowing
  // the internal deps (used by retry affordances).
  const [reloadSeed, setReloadSeed] = useState(0)

  // Coordination state survives effect restarts. The object is keyed by the
  // DEPS (the resource identity): a refresh reuses it, a new resource gets a
  // fresh one.
  const resourceRef = useRef<ResourceControl | null>(null)
  const previousDeps = useRef<DependencyList | null>(null)

  useEffect(() => {
    // A run whose deps did not change is a REFRESH of the SAME resource
    // (reloadSeed / pollMs triggered). A deps change is a NEW resource.
    const isRefresh =
      previousDeps.current !== null && sameDeps(previousDeps.current, deps)
    previousDeps.current = deps
    const resource =
      isRefresh && resourceRef.current !== null
        ? resourceRef.current
        : (resourceRef.current = { busy: 0, pending: false, request: 0 })
    const current = resourceRef.current

    // Writes are admitted ONLY for the still-current resource and only from
    // the latest-issued token — this replaces the old per-run alive flag and
    // is Strict-Mode-safe (a discarded first-effect load still owns the
    // newest token until something newer is issued, so its delivery is not
    // lost).
    const maybeApply = (write: (previous: ResourceState<T>) => ResourceState<T>, token: number) => {
      if (resourceRef.current !== current || token !== resource.request) {
        return
      }
      setState(write)
    }

    const load = () => {
      const token = ++resource.request
      resource.busy += 1
      return loader().then(
        (value) => {
          maybeApply(() => ({ data: value, error: null, loading: false }), token)
        },
        (cause: unknown) => {
          maybeApply(
            (previous) => ({ data: previous.data, error: errorText(cause), loading: false }),
            token,
          )
        },
      ).finally(() => {
        resource.busy -= 1
        // Run a refresh that was deferred because a load was busy — exactly
        // one extra run per deferred intent, and only while THIS resource is
        // still the one on screen (a deferred intent for a navigated-away
        // resource is dropped, navigation won).
        if (resourceRef.current === current && resource.pending && resource.busy === 0) {
          resource.pending = false
          setReloadSeed((n) => n + 1)
        }
      })
    }

    // A refresh NEVER clears prior data: open forms (e.g. a sibling row in
    // edit mode) must survive a list reload, and a failed refresh keeps the
    // last good data visible under its note instead of an error-only card.
    // Only a genuinely NEW resource starts from the loading reset.
    setState((previous) =>
      isRefresh && previous.data !== null
        ? previous
        : { data: null, error: null, loading: true },
    )
    // Start a load unless a load for THIS SAME RESOURCE is already in
    // flight (its fresh response still applies — it owns the newest token).
    // This keeps Strict Mode's discarded first effect working AND avoids a
    // second identical request on refresh-style restarts.
    if (!isRefresh || resource.busy === 0) {
      void load()
    }

    // Polls skip a beat while a load is in flight — the network stays calm
    // and responses cannot pile up on each other.
    const poll = () => {
      if (resource.busy === 0) {
        void load()
      }
    }
    const id = pollMs > 0 ? window.setInterval(poll, pollMs) : undefined
    return () => {
      // The response/token guards above admit only the current resource, so
      // no extra bookkeeping is needed beyond stopping the interval.
      if (id !== undefined) {
        window.clearInterval(id)
      }
    }
    // The caller owns the dependency list, same contract as useEffect.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, pollMs, reloadSeed])

  return {
    ...state,
    // A refresh while a load of this resource is already underway would
    // start a second IDENTICAL request: queue it to run once when the
    // in-flight load settles instead of overlapping.
    refetch: () => {
      const resource = resourceRef.current
      if (resource === null) {
        return
      }
      if (resource.busy > 0) {
        resource.pending = true
        return
      }
      setReloadSeed((n) => n + 1)
    },
  }
}

/** Re-renders the mounted component every `intervalMs` — for live timers. */
export function useReRender(intervalMs: number): void {
  const [, setTick] = useState(0)
  useEffect(() => {
    const id = window.setInterval(() => setTick((n) => n + 1), intervalMs)
    return () => window.clearInterval(id)
  }, [intervalMs])
}

/** Seconds elapsed on the monotonic clock between two performance anchors. */
export function elapsedSinceAnchor(
  serverSecondsAtAnchor: number,
  clientPerfAtAnchor: number,
  clientPerfNow: number,
): number {
  return Math.floor(
    serverSecondsAtAnchor + Math.max(0, (clientPerfNow - clientPerfAtAnchor) / 1000),
  )
}

interface LiveAnchor {
  /** Identity of the session snapshot this anchor was taken from. */
  key: string
  serverSecondsAtAnchor: number
  clientPerfAtAnchor: number
}

/**
 * Live work seconds for a study session, IMMUNE to device-clock skew.
 *
 * The server computes activeSecondsTotal AT serverNow. When that snapshot
 * arrives, whatever instant the client is at becomes the ANCHOR on the
 * client's monotonic clock (performance.now) — counting only genuinely
 * elapsed monotonic time from there. Wall-clock arithmetic against serverNow
 * is never used, so a skewed device clock cannot inject phantom minutes.
 * The anchor is re-taken on every fresh snapshot (key includes serverNow +
 * total), so refreshes re-anchor instead of drifting.
 *
 * Non-ACTIVE sessions return the stored total unchanged; null when no
 * session data is present.
 */
export function useLiveStudySeconds(session: StudySession | null): number | null {
  const nowPerf = usePerformanceTick(1000)

  const key = session === null
    ? null
    : `${session.id}:${session.activeSecondsTotal}:${session.serverNow}`

  const [anchor, setAnchor] = useState<LiveAnchor | null>(null)

  useEffect(() => {
    // Re-anchor OUTSIDE the render path, idempotently: identical snapshots
    // keep their anchor; fresh snapshots (or null) replace it.
    if (session === null || key === null) {
      setAnchor((previous) => (previous === null ? previous : null))
      return
    }
    setAnchor((previous) => (
      previous !== null && previous.key === key
        ? previous
        : {
            key,
            serverSecondsAtAnchor: session.activeSecondsTotal,
            clientPerfAtAnchor: performance.now(),
          }
    ))
  }, [session, key])

  const running = session !== null && session.status === 'ACTIVE' && session.activeSince !== null

  if (session === null) {
    return null
  }
  // Before the anchoring effect has run for a fresh snapshot, the server
  // total itself is the honest display value (one frame at most).
  if (anchor === null || anchor.key !== key || !running) {
    return session.activeSecondsTotal
  }
  return elapsedSinceAnchor(
    anchor.serverSecondsAtAnchor,
    anchor.clientPerfAtAnchor,
    nowPerf,
  )
}

/**
 * Monotonic ticking clock value, set ONLY from the interval callback (never
 * synchronously in an effect), so renders stay pure and skew-immune.
 */
export function usePerformanceTick(intervalMs: number): number {
  const [perf, setPerf] = useState(() => performance.now())
  useEffect(() => {
    const id = window.setInterval(() => setPerf(performance.now()), intervalMs)
    return () => window.clearInterval(id)
  }, [intervalMs])
  return perf
}
