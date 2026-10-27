import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createFakeRuntime,
  findNode,
  installFakeWindow,
  tick,
  type FakeHookNode,
} from '../test-support/fakeReact'
import type { PageEnvelope, StudySession } from '../api/types'

/*
 * White-box regression tests for the Study hub's Phase-1 refresh fix: the
 * focused (ACTIVE) card and the history list keep STABLE resource
 * identities — lifecycle actions refetch both in a coordinated way instead
 * of bumping a shared refresh key (which used to clear the rows, unmount the
 * focused card mid-action and blank the panel after a failed refresh).
 * Driving the REAL StudyPage component through the minimal fake React
 * runtime.
 */

const fake = createFakeRuntime()

const api = {
  createStudySession: vi.fn(),
  fetchCurrentStudySession: vi.fn(),
  fetchProjects: vi.fn(),
  fetchProjectSections: vi.fn(),
  fetchStudyHistory: vi.fn(),
  fetchStudySessionsByContext: vi.fn(),
  finishStudySession: vi.fn(),
  pauseStudySession: vi.fn(),
  resumeStudySession: vi.fn(),
  startStudySession: vi.fn(),
}

const plannedRow: StudySession = {
  id: 'x1',
  title: 'Focus',
  summary: null,
  status: 'PLANNED',
  context: null,
  createdAt: '2026-10-26T10:00:00Z',
  startedAt: null,
  activeSince: null,
  pausedAt: null,
  completedAt: null,
  activeSecondsTotal: 0,
  serverNow: '2026-10-26T10:00:00Z',
}

interface HistoryRequest {
  page: number
  resolve: (value: PageEnvelope<StudySession>) => void
  reject: (cause: unknown) => void
}

let currentResolvers: Array<(session: StudySession | null) => void>
let historyRequests: HistoryRequest[]

beforeEach(() => {
  fake.reset()
  installFakeWindow()

  vi.doMock('react', () => ({
    Fragment: 'Fragment',
    ...fake.react,
    useCallback: (fn: (...args: unknown[]) => unknown) => fn,
  }))
  const routeParams = new URLSearchParams('')
  vi.doMock('react-router-dom', () => ({
    Link: 'Link',
    useSearchParams: () => [routeParams, vi.fn()],
  }))
  vi.doMock('../api/dashboard', () => api)

  for (const mock of Object.values(api)) {
    mock.mockReset()
  }
  currentResolvers = []
  historyRequests = []
  api.fetchCurrentStudySession.mockImplementation(
    () =>
      new Promise<StudySession | null>((resolve) => {
        currentResolvers.push(resolve)
      }),
  )
  api.fetchStudyHistory.mockImplementation(
    (page: number) =>
      new Promise<PageEnvelope<StudySession>>((resolve, reject) => {
        historyRequests.push({ page, resolve, reject })
      }),
  )
})

afterEach(() => {
  vi.resetModules()
})

async function loadPage() {
  return (await import('./Study')).default
}

function envelope(content: StudySession[] = []): PageEnvelope<StudySession> {
  return { content, page: 0, size: 20, totalElements: 1, totalPages: 1 }
}

const byName = (tree: unknown, name: string): FakeHookNode | undefined =>
  findNode(
    tree,
    (n) => typeof n.type === 'function' && n.type.name === name,
  )
const byType = (tree: unknown, type: string): FakeHookNode | undefined =>
  findNode(tree, (n) => n.type === type)

/** Found-or-fail: keeps later prop dereferences honest and direct. */
function required(node: FakeHookNode | undefined, label: string): FakeHookNode {
  if (node === undefined) {
    throw new Error(`${label} not found in the rendered tree`)
  }
  return node
}

/**
 * First render issues the two mount loads; settling them and re-rendering
 * ends loading. History starts with one PLANNED row.
 */
async function mountAndSettle(StudyPage: () => unknown): Promise<unknown> {
  fake.render(() => StudyPage())
  currentResolvers[0]?.(null)
  historyRequests[0]?.resolve(envelope([plannedRow]))
  await tick()
  return fake.render(() => StudyPage())
}

describe('Study hub refresh coordination', () => {
  it('a lifecycle action refetches BOTH resources without clearing the history', async () => {
    const StudyPage = await loadPage()
    const tree = await mountAndSettle(StudyPage)

    expect(byName(tree, 'HistoryRow')).toBeDefined()
    // The focused card's onAction is the parent's coordinated refetch.
    const focused = required(byName(tree, 'FocusedSessionCard'), 'FocusedSessionCard')
    ;(focused.props.onAction as () => void)()
    fake.render(() => StudyPage())

    expect(api.fetchCurrentStudySession).toHaveBeenCalledTimes(2)
    expect(api.fetchStudyHistory).toHaveBeenCalledTimes(2)
    expect(api.fetchStudyHistory.mock.calls[1]).toEqual([0, 20])
    // Rows are retained while the refresh runs.
    expect(byName(fake.render(() => StudyPage()), 'HistoryRow')).toBeDefined()

    historyRequests[1]?.resolve(envelope([plannedRow]))
    await tick()
    expect(byName(fake.render(() => StudyPage()), 'HistoryRow')).toBeDefined()
    // Settling must not schedule any further request.
    fake.render(() => StudyPage())
    expect(api.fetchStudyHistory).toHaveBeenCalledTimes(2)
  })

  it('a second refetch while a refresh runs is queued once, never overlapped', async () => {
    const StudyPage = await loadPage()
    const tree = await mountAndSettle(StudyPage)

    const focused = required(byName(tree, 'FocusedSessionCard'), 'FocusedSessionCard')
    ;(focused.props.onAction as () => void)()
    fake.render(() => StudyPage())
    expect(api.fetchStudyHistory).toHaveBeenCalledTimes(2)

    // Second click while the first refresh is in flight: one queued intent.
    ;(focused.props.onAction as () => void)()
    expect(api.fetchStudyHistory).toHaveBeenCalledTimes(2)

    historyRequests[1]?.resolve(envelope([plannedRow]))
    await tick()
    // The queued intent fires exactly once after the settle.
    fake.render(() => StudyPage())
    expect(api.fetchStudyHistory).toHaveBeenCalledTimes(3)
    historyRequests[2]?.resolve(envelope([plannedRow]))
    await tick()
    fake.render(() => StudyPage())
    expect(api.fetchStudyHistory).toHaveBeenCalledTimes(3)
  })

  it('a failed history refresh keeps the last good rows under a stale-note', async () => {
    const StudyPage = await loadPage()
    const tree = await mountAndSettle(StudyPage)

    const focused = required(byName(tree, 'FocusedSessionCard'), 'FocusedSessionCard')
    ;(focused.props.onAction as () => void)()
    fake.render(() => StudyPage())

    historyRequests[1]?.reject(new Error('boom'))
    await tick()
    const afterFailure = fake.render(() => StudyPage())

    expect(byName(afterFailure, 'HistoryRow')).toBeDefined()
    const stale = findNode(
      afterFailure,
      (n) => n.type === 'div' && String(n.props.className).includes('stale-note'),
    )
    expect(stale).toBeDefined()
    // No further requests after the failure settled.
    fake.render(() => StudyPage())
    expect(api.fetchStudyHistory).toHaveBeenCalledTimes(2)
  })

  it('mounted pages stay honest: no picker fetch while the create panel is closed', async () => {
    const StudyPage = await loadPage()
    const tree = await mountAndSettle(StudyPage)

    // The CreateSessionPanel element is mounted as a CHILD (not invoked by
    // the fake runtime), so module-level refetch coordination must not leak
    // extra picker loads either.
    expect(byType(tree, 'section')).toBeDefined()
    expect(api.fetchProjects).not.toHaveBeenCalled()
    expect(api.fetchProjectSections).not.toHaveBeenCalled()
  })
})
