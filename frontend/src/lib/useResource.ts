import { useEffect, useState } from 'react'
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

/**
 * Fetch-once-per-deps resource state with an alive-guard so a slow response
 * of a discarded render pass can never overwrite a newer one.
 */
export function useResource<T>(loader: () => Promise<T>, deps: DependencyList): ResourceState<T> {
  const [state, setState] = useState<ResourceState<T>>({
    data: null,
    error: null,
    loading: true,
  })

  useEffect(() => {
    let alive = true
    setState({ data: null, error: null, loading: true })
    loader().then(
      (value) => {
        if (alive) {
          setState({ data: value, error: null, loading: false })
        }
      },
      (cause: unknown) => {
        if (alive) {
          setState({ data: null, error: errorText(cause), loading: false })
        }
      },
    )
    return () => {
      alive = false
    }
    // The caller owns the dependency list, same contract as useEffect.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return state
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
