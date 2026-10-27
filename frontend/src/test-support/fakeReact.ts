/**
 * Minimal React hook runtime substitute for WHITE-BOX regression tests of
 * hooks/components that are driven by hook order. It mimics just enough of
 * React: ordered hook slots persisted across renders, functional setState,
 * useEffect with shallow dep comparison and cleanup chaining.
 *
 * Scope and honest limits: one hook USER at a time (call reset() between
 * uses), no batching/concurrent rendering, no context/refs beyond useRef.
 * It is NOT a React replacement — it exists so reliability regressions of
 * useResource and the project-draft flow are pinned by `npm test` without
 * adding any dependency (the Playwright/browser-test decision stays open).
 */

export type FakeHookNode = { type: unknown; props: Record<string, unknown> }

interface FakeRuntime {
  react: Record<string, unknown>
  /** Renders one "component" pass and flushes its newly-queued effects. */
  render<T>(component: () => T): T
  /** Clear all hook slots/effects — call before starting a NEW hook user. */
  reset(): void
}

export function createFakeRuntime(): FakeRuntime {
  // One shared slot list preserves React's "hooks consume slots in call
  // order" rule for the single hook user under test.
  let slots: Array<Record<string, unknown> | undefined> = []
  let cursor = 0
  let queuedEffects: Array<() => void> = []

  function useSlot<T>(initial: T | (() => T)): { current: unknown; value: T } {
    const index = cursor++
    // Strict slot identity: value objects are never replaced once created
    // (except explicit set), so effect dep comparisons stay stable.
    if (slots[index] === undefined) {
      slots[index] = {
        value:
          typeof initial === 'function' ? (initial as () => T)() : initial,
      }
    }
    return slots[index] as { current: unknown; value: T }
  }

  const react = {
    useState<T>(initial: T | (() => T)): [T, (next: T | ((previous: T) => T)) => void] {
      const slot = useSlot<T>(initial)
      return [
        slot.value,
        (next: T | ((previous: T) => T)) => {
          slot.value =
            typeof next === 'function' ? (next as (previous: T) => T)(slot.value) : next
        },
      ]
    },
    useRef<T>(initial: T): { current: T } {
      const index = cursor++
      if (slots[index] === undefined) {
        slots[index] = { current: initial }
      }
      return slots[index] as { current: T }
    },
    useEffect(effect: () => void | (() => void), deps: unknown[]) {
      const index = cursor++
      const previous = slots[index] as
        | { deps: unknown[]; cleanup?: () => void }
        | undefined
      const changed =
        previous === undefined ||
        deps === undefined ||
        previous.deps.length !== deps.length ||
        deps.some((value, i) => !Object.is(value, previous.deps[i]))
      if (!changed) {
        return
      }
      queuedEffects.push(() => {
        previous?.cleanup?.()
        const cleanup = effect()
        slots[index] = { deps: [...deps], cleanup }
      })
    },
  }

  return {
    react,
    render(component) {
      cursor = 0
      const value = component()
      // Drain ALL newly-queued effects of this pass (including ones queued
      // by an earlier batch run) — mirrors React, where effects scheduled by
      // this render run before the next render observes state.
      while (queuedEffects.length > 0) {
        const batch = queuedEffects
        queuedEffects = []
        batch.forEach((run) => run())
      }
      return value
    },
    reset() {
      slots = []
      cursor = 0
      queuedEffects = []
    },
  }
}

/** useResource touches window.setInterval/clearInterval — stub them here. */
export function installFakeWindow(): void {
  const globalRecord = globalThis as Record<string, unknown>
  globalRecord.window = globalRecord.window ?? {
    setInterval: () => 1,
    clearInterval: () => undefined,
  }
}

/** Depth-first search over the plain-object JSX tree. */
export function findNode(
  node: unknown,
  predicate: (node: FakeHookNode) => boolean,
): FakeHookNode | undefined {
  if (!node || typeof node !== 'object') {
    return undefined
  }
  const candidate = node as FakeHookNode
  if (candidate.type !== undefined && candidate.props !== undefined && predicate(candidate)) {
    return candidate
  }
  const children = (candidate.props as { children?: unknown } | undefined)?.children
  for (const child of [children].flat(Infinity)) {
    const hit = findNode(child, predicate)
    if (hit !== undefined) {
      return hit
    }
  }
  return undefined
}

/** A JSX event handler as captured in the plain-object walk tree. */
export type FakeHandler = (...args: unknown[]) => unknown

/** Typed prop read: the tree is plain objects, handlers need casting once. */
export function handler(
  node: FakeHookNode | undefined,
  name = 'onClick',
): FakeHandler | undefined {
  return (node?.props[name] ?? undefined) as FakeHandler | undefined
}

/** Drain pending microtasks (promise chains settle across several turns). */
export async function tick(times = 10): Promise<void> {
  for (let i = 0; i < times; i += 1) {
    await Promise.resolve()
  }
}
