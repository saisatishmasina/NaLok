import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createFakeRuntime,
  installFakeWindow,
  tick,
} from '../test-support/fakeReact'

/*
 * White-box regression tests for useResource's refresh coordination, driven
 * through a minimal React-runtime substitute (see test-support/fakeReact).
 * Each test pins one reviewer-reported situation against the REAL hook code:
 *
 * 1. A queued refresh for the CURRENT resource must survive a drained load
 *    of an older resource (per-resource coordination, no shared flag).
 * 2. A refresh-style restart (pollMs change) while a load is in flight must
 *    not issue a second identical request.
 * 3. refetch() while busy never overlaps the running request: it is queued
 *    and runs exactly once after the in-flight load settles.
 */

let fake: ReturnType<typeof createFakeRuntime>

beforeEach(() => {
  fake = createFakeRuntime()
  vi.doMock('react', () => fake.react)
  installFakeWindow()
})

afterEach(() => {
  vi.resetModules()
})

// Load the hook AFTER react is mocked so the hook binds the fake runtime.
async function loadHook() {
  const module = await import('./useResource')
  return module.useResource
}

describe('useResource refresh coordination', () => {
  it('keeps a queued refresh of the CURRENT resource when an older resource load drains', async () => {
    const useResource = await loadHook()
    let depsKey = 'A'
    const requests: Array<{ key: string; resolve: (value: string) => void }> = []
    const loader = () =>
      new Promise<string>((resolve) => {
        requests.push({ key: depsKey, resolve })
      })

    // Mount A, navigate to B, refetch while B's first load is in flight.
    fake.render(() => useResource(loader, [depsKey], 0))
    depsKey = 'B'
    fake.render(() => useResource(loader, [depsKey], 0))
    fake.render(() => useResource(loader, [depsKey], 0)).refetch()
    expect(requests.filter((r) => r.key === 'B')).toHaveLength(1)

    // The OLD resource's load drains LAST and must not steal/erase B's
    // queued intent — it only affects its own (already orphaned) resource.
    requests.find((r) => r.key === 'A')?.resolve('A final')
    await tick()
    expect(requests.filter((r) => r.key === 'B')).toHaveLength(1)

    // B's first load settles → the queued intent remains until the reloadSeed
    // re-render; a render then runs the deferred refresh exactly once.
    requests[requests.length - 1].resolve('B snapshot')
    await tick()
    const interim = fake.render(() => useResource(loader, [depsKey], 0)) as unknown as {
      data: string | null
    }
    expect(requests.filter((r) => r.key === 'B')).toHaveLength(2)
    expect(interim.data).toBe('B snapshot')

    // The deferred refresh resolves and wins the write.
    requests[requests.length - 1].resolve('B final')
    await tick()
    const state = fake.render(() => useResource(loader, [depsKey], 0)) as {
      data: string | null
      error: string | null
      loading: boolean
    }
    expect(state.data).toBe('B final')
    expect(state.loading).toBe(false)
  })

  it('does not start a second identical request when only the poll interval changes', async () => {
    const useResource = await loadHook()
    const requests: Array<{ resolve: (value: string) => void }> = []
    const loader = () =>
      new Promise<string>((resolve) => {
        requests.push({ resolve })
      })

    fake.render(() => useResource(loader, ['same'], 1_000))
    expect(requests).toHaveLength(1)

    // Same resource, new interval, previous load still in flight: the
    // restart must reuse the running load instead of duplicating it.
    fake.render(() => useResource(loader, ['same'], 2_000))
    expect(requests).toHaveLength(1)

    requests[0]?.resolve('fresh')
    await tick()
    const state = fake.render(() => useResource(loader, ['same'], 2_000)) as {
      data: string | null
    }
    expect(requests).toHaveLength(1)
    expect(state.data).toBe('fresh')
  })

  it('queues refetch while busy and runs it at most once after settle', async () => {
    const useResource = await loadHook()
    const requests: Array<{ resolve: (value: number) => void }> = []
    const loader = () =>
      new Promise<number>((resolve) => {
        requests.push({ resolve })
      })

    fake.render(() => useResource(loader, ['one'], 0))
    // Two refetch clicks while the first load is in flight: ONE queued
    // intent total, never two overlapping requests.
    const first = fake.render(() => useResource(loader, ['one'], 0))
    first.refetch()
    const second = fake.render(() => useResource(loader, ['one'], 0))
    second.refetch()
    expect(requests).toHaveLength(1)

    requests[0]?.resolve(1)
    await tick()
    // The queued intent fires on the next render and produces exactly ONE
    // follow-up request...
    fake.render(() => useResource(loader, ['one'], 0))
    expect(requests).toHaveLength(2)
    requests[1]?.resolve(2)
    await tick()
    // ...and nothing else was scheduled after it settled.
    fake.render(() => useResource(loader, ['one'], 0))
    expect(requests).toHaveLength(2)
    const state = fake.render(() => useResource(loader, ['one'], 0)) as {
      data: number | null
    }
    expect(state.data).toBe(2)
  })
})
