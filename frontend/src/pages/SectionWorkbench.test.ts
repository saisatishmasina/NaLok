import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createFakeRuntime,
  findNode,
  handler,
  installFakeWindow,
  tick,
  type FakeHookNode,
} from '../test-support/fakeReact'
import type { PageEnvelope, ProjectSection, StudySession } from '../api/types'

/*
 * White-box regression tests for the F08 section workbench reliability fixes
 * (Phase-1), run against the REAL Workbench code through the minimal fake
 * React runtime. Each test pins one reviewed defect:
 *
 * 1. Planning/lifecycle actions used to bump a shared refresh key, which
 *    RECREATED every resource identity — clearing data, unmounting the hero
 *    (destroying open edit drafts) and, after a failed refresh, blanking the
 *    page. Now actions call coordinated refetch() on stable identities.
 * 2. A refetch issued while an identical GET is in flight must be QUEUED
 *    (once), never overlapped as a second identical request.
 * 3. The by-context list is paginated; a hop to another section resets the
 *    page and NEVER reloads the whole project's sections; a hop with an
 *    unsaved draft asks before discarding it.
 * 4. The plan card title is prefilled from the section (docs flow) and stays
 *    editable; typed-but-unplanned content reports itself to the hop guard.
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
  updateSection: vi.fn(),
}

const sectionA: ProjectSection = {
  id: 's1',
  projectId: 'p1',
  title: 'Alpha',
  description: null,
  position: 0,
  version: 0,
}
const sectionB: ProjectSection = {
  id: 's2',
  projectId: 'p1',
  title: 'Beta',
  description: null,
  position: 1,
  version: 0,
}

const plannedRow: StudySession = {
  id: 'x1',
  title: 'Focus',
  summary: null,
  status: 'PLANNED',
  context: { entityType: 'PROJECT_SECTION', referenceId: 's1' },
  createdAt: '2026-10-26T10:00:00Z',
  startedAt: null,
  activeSince: null,
  pausedAt: null,
  completedAt: null,
  activeSecondsTotal: 0,
  serverNow: '2026-10-26T10:00:00Z',
}

interface ListRequest {
  page: number
  resolve: (value: PageEnvelope<StudySession>) => void
  reject: (cause: unknown) => void
}

let sectionsResolvers: Array<(sections: ProjectSection[]) => void>
let currentResolvers: Array<(session: StudySession | null) => void>
let listRequests: ListRequest[]
let confirmSpy: ReturnType<typeof vi.fn>

beforeEach(() => {
  fake.reset()
  installFakeWindow()
  confirmSpy = vi.fn(() => true)
  window.confirm = confirmSpy as unknown as typeof window.confirm

  vi.doMock('react', () => ({
    Fragment: 'Fragment',
    ...fake.react,
    useCallback: (fn: (...args: unknown[]) => unknown) => fn,
  }))
  vi.doMock('react-router-dom', () => ({
    Link: 'Link',
    useParams: () => ({ projectId: 'p1', sectionId: 's1' }),
    useSearchParams: () => [new URLSearchParams(''), vi.fn()],
  }))
  vi.doMock('../api/dashboard', () => api)

  for (const mock of Object.values(api)) {
    mock.mockReset()
  }
  sectionsResolvers = []
  currentResolvers = []
  listRequests = []
  api.fetchProjectSections.mockImplementation(
    () =>
      new Promise<ProjectSection[]>((resolve) => {
        sectionsResolvers.push(resolve)
      }),
  )
  api.fetchCurrentStudySession.mockImplementation(
    () =>
      new Promise<StudySession | null>((resolve) => {
        currentResolvers.push(resolve)
      }),
  )
  api.fetchStudySessionsByContext.mockImplementation(
    (_entityType: string, _referenceId: string, page: number) =>
      new Promise<PageEnvelope<StudySession>>((resolve, reject) => {
        listRequests.push({ page, resolve, reject })
      }),
  )
})

afterEach(() => {
  vi.resetModules()
})

async function loadModule() {
  return await import('./SectionWorkbench')
}

function envelope(
  page: number,
  totalPages = 1,
  content: StudySession[] = [],
): PageEnvelope<StudySession> {
  return { content, page, size: 20, totalElements: 25, totalPages }
}

const byName = (tree: unknown, name: string): FakeHookNode | undefined =>
  findNode(
    tree,
    (n) => typeof n.type === 'function' && n.type.name === name,
  )

/** Found-or-fail: keeps later prop dereferences honest and direct. */
function required(node: FakeHookNode | undefined, label: string): FakeHookNode {
  if (node === undefined) {
    throw new Error(`${label} not found in the rendered tree`)
  }
  return node
}

/**
 * First render issues the three mount loads; settling them and re-rendering
 * ends loading. Returns the settled tree.
 */
async function mountAndSettle(
  Workbench: (props: { projectId: string; sectionId: string }) => unknown,
  initialList: PageEnvelope<StudySession> = envelope(0, 1, [plannedRow]),
): Promise<unknown> {
  fake.render(() => Workbench({ projectId: 'p1', sectionId: 's1' }))
  sectionsResolvers[0]?.([sectionA, sectionB])
  listRequests[0]?.resolve(initialList)
  currentResolvers[0]?.(null)
  await tick()
  return fake.render(() => Workbench({ projectId: 'p1', sectionId: 's1' }))
}

describe('SectionWorkbench refresh identities', () => {
  it('planning refetches sessions in place without reloading sections or unmounting the hero', async () => {
    const { Workbench, StudyCard, SectionSessionsCard } = await loadModule()
    const tree = await mountAndSettle(Workbench)

    const studyCard = required(findNode(tree, (n) => n.type === StudyCard), 'StudyCard')
    expect(byName(tree, 'SectionHero')).toBeDefined()

    // The documented flow: planning refetches the TWO session resources and
    // leaves the sections resource untouched (stable identity).
    ;(studyCard.props.onPlanned as () => void)()
    const afterAction = fake.render(() => Workbench({ projectId: 'p1', sectionId: 's1' })) as unknown

    expect(api.fetchProjectSections).toHaveBeenCalledTimes(1)
    expect(api.fetchCurrentStudySession).toHaveBeenCalledTimes(2)
    expect(listRequests).toHaveLength(2)
    expect(api.fetchStudySessionsByContext.mock.calls[1]).toEqual([
      'PROJECT_SECTION',
      's1',
      0,
      20,
    ])
    // The hero cannot be unmounted by a refresh — open edit drafts survive.
    expect(byName(afterAction, 'SectionHero')).toBeDefined()

    // The refetched list is DELIVERED: the identity never changed away.
    listRequests[1]?.resolve(envelope(0, 1, [plannedRow]))
    await tick()
    const settled = fake.render(() => Workbench({ projectId: 'p1', sectionId: 's1' })) as unknown
    const sessions = required(
      findNode(settled, (n) => n.type === SectionSessionsCard),
      'SectionSessionsCard',
    )
    const list = (sessions.props.list as { data: PageEnvelope<StudySession> }).data
    expect(list.page).toBe(0)

    // Settling must not schedule any further request.
    fake.render(() => Workbench({ projectId: 'p1', sectionId: 's1' }))
    expect(listRequests).toHaveLength(2)
  })

  it('a refetch issued while the identical GET is in flight is queued once, never overlapped', async () => {
    const { Workbench, StudyCard } = await loadModule()
    // Mount with an UNRESOLVED first by-context GET.
    fake.render(() => Workbench({ projectId: 'p1', sectionId: 's1' }))
    sectionsResolvers[0]?.([sectionA])
    currentResolvers[0]?.(null)
    await tick()
    const tree = fake.render(() => Workbench({ projectId: 'p1', sectionId: 's1' }))

    const studyCard = required(findNode(tree, (n) => n.type === StudyCard), 'StudyCard')
    ;(studyCard.props.onPlanned as () => void)()
    fake.render(() => Workbench({ projectId: 'p1', sectionId: 's1' }))
    // The in-flight GET is REUSED for the refresh: still exactly one request.
    expect(listRequests).toHaveLength(1)

    listRequests[0]?.resolve(envelope(0, 1))
    await tick()
    // Exactly one queued intent fires after the settle.
    fake.render(() => Workbench({ projectId: 'p1', sectionId: 's1' }))
    expect(listRequests).toHaveLength(2)
    listRequests[1]?.resolve(envelope(0, 1, [plannedRow]))
    await tick()
    fake.render(() => Workbench({ projectId: 'p1', sectionId: 's1' }))
    expect(listRequests).toHaveLength(2)
  })

  it('a failed sessions refresh keeps the last good list and the section', async () => {
    const { Workbench, SectionSessionsCard } = await loadModule()
    const first = envelope(0, 1, [plannedRow])
    const tree = await mountAndSettle(Workbench, first)

    // Refresh through the card's wiring (its Refresh button / lifecycle
    // onAction) — then let the reload FAIL.
    const sessions = required(
      findNode(tree, (n) => n.type === SectionSessionsCard),
      'SectionSessionsCard',
    )
    ;(sessions.props.onAction as () => void)()
    fake.render(() => Workbench({ projectId: 'p1', sectionId: 's1' }))

    listRequests[1]?.reject(new Error('boom'))
    await tick()
    const afterFailure = fake.render(() => Workbench({ projectId: 'p1', sectionId: 's1' })) as unknown

    const refreshed = required(
      findNode(afterFailure, (n) => n.type === SectionSessionsCard),
      'SectionSessionsCard',
    )
    expect((refreshed.props.list as { data: unknown }).data).toEqual(first)
    expect((refreshed.props.list as { error: unknown }).error).toBe('boom')
    // The section STILL renders over the retained data.
    expect(byName(afterFailure, 'SectionHero')).toBeDefined()
    // No further requests after the failure settle.
    fake.render(() => Workbench({ projectId: 'p1', sectionId: 's1' }))
    expect(listRequests).toHaveLength(2)
    expect(api.fetchProjectSections).toHaveBeenCalledTimes(1)
  })

  it('pages the by-context list; hops reset the page and reload NO sections; drafts ask first', async () => {
    const { Workbench, SectionSessionsCard } = await loadModule()
    // Two-page list: content beyond page 0 must be reachable.
    const tree = await mountAndSettle(Workbench, envelope(0, 2))

    const renderWorkbench = () =>
      fake.render(() => Workbench({ projectId: 'p1', sectionId: 's1' })) as unknown
    const sessionsCard = () =>
      required(
        findNode(renderWorkbench(), (n) => n.type === SectionSessionsCard),
        'SectionSessionsCard',
      )
    // The hop handler is recreated per render (it reads the CURRENT guard
    // state, like a real click would): always grab the latest closure.
    const grabHop = () => required(byName(renderWorkbench(), 'SiblingNav'), 'SiblingNav')

    // Hop with NO open drafts: no confirm prompt. The page is already 0 in
    // this state, so resetting it stays a no-op (no extra request).
    expect(byName(tree, 'SiblingNav')).toBeDefined()
    const preventSpy = vi.fn()
    ;(grabHop().props.onHop as (event: { preventDefault: () => void }) => void)({
      preventDefault: preventSpy,
    })
    expect(confirmSpy).not.toHaveBeenCalled()
    expect(preventSpy).not.toHaveBeenCalled()
    expect(listRequests).toHaveLength(1)
    expect(api.fetchProjectSections).toHaveBeenCalledTimes(1)

    // Going to page 1 issues the page-1 request.
    ;(sessionsCard().props.onPage as (page: number) => void)(1)
    renderWorkbench()
    expect(listRequests).toHaveLength(2)
    expect(api.fetchStudySessionsByContext.mock.calls[1]).toEqual([
      'PROJECT_SECTION',
      's1',
      1,
      20,
    ])
    expect(sessionsCard().props.listPage).toBe(1)
    listRequests[1]?.resolve(envelope(1, 2))
    await tick()

    // An open editor draft ARMED the guard for this section.
    const hero = required(byName(renderWorkbench(), 'SectionHero'), 'SectionHero')
    ;(hero.props.onEditingChange as (id: string | null) => void)('s1')
    renderWorkbench()

    // Declining the confirm prevents the hop AND the page reset.
    confirmSpy = vi.fn(() => false)
    window.confirm = confirmSpy as unknown as typeof window.confirm
    ;(grabHop().props.onHop as (event: { preventDefault: () => void }) => void)({
      preventDefault: preventSpy,
    })
    expect(confirmSpy).toHaveBeenCalledTimes(1)
    expect(preventSpy).toHaveBeenCalledTimes(1)
    renderWorkbench()
    expect(listRequests).toHaveLength(2)
    expect(sessionsCard().props.listPage).toBe(1)

    // Confirming the hop resets the list page for the new section.
    confirmSpy = vi.fn(() => true)
    window.confirm = confirmSpy as unknown as typeof window.confirm
    ;(grabHop().props.onHop as (event: { preventDefault: () => void }) => void)({
      preventDefault: preventSpy,
    })
    expect(confirmSpy).toHaveBeenCalledTimes(1)
    expect(preventSpy).toHaveBeenCalledTimes(1)
    renderWorkbench()
    expect(listRequests).toHaveLength(3)
    expect(listRequests[2]?.page).toBe(0)
    expect(sessionsCard().props.listPage).toBe(0)
    // Hops NEVER reload the whole project's sections.
    expect(api.fetchProjectSections).toHaveBeenCalledTimes(1)
  })
})

describe('StudyCard plan prefill and dirty reporting', () => {
  it('prefills the title from the section, keeps it editable, and clears the guard after planning', async () => {
    const { StudyCard } = await loadModule()
    api.createStudySession.mockResolvedValue(plannedRow)

    let lastDirty: string | null | undefined
    const renderCard = (onPlanned = () => undefined) =>
      fake.render(() =>
        StudyCard({
          section: sectionA,
          onPlanned,
          onDirtyChange: (id) => {
            lastDirty = id
          },
        }),
      ) as unknown

    let tree: unknown = renderCard()

    const titleValue = () =>
      findNode(
        tree,
        (n) => n.type === 'input' && n.props.id === 'plan-title',
      )?.props.value as string

    // Documented flow: the plan card is prefilled from the section.
    expect(titleValue(), 'prefilled title').toBe('Alpha')
    const planButton = required(
      findNode(tree, (n) => n.type === 'button' && n.props.children === 'Plan session'),
      'Plan button',
    )
    expect((planButton.props as { disabled: boolean }).disabled).toBe(false)

    // Still editable.
    const titleInput = required(
      findNode(tree, (n) => n.type === 'input' && n.props.id === 'plan-title'),
      'Title input',
    )
    handler(titleInput, 'onChange')?.({ target: { value: 'Evening review' } })
    tree = renderCard()
    expect(titleValue()).toBe('Evening review')
    expect(lastDirty, 'typed plan arms the hop guard').toBe('s1')

    // Typing BACK to the prefill deactivates the guard again.
    const reverted = required(
      findNode(tree, (n) => n.type === 'input' && n.props.id === 'plan-title'),
      'Title input',
    )
    handler(reverted, 'onChange')?.({ target: { value: sectionA.title } })
    tree = renderCard()
    expect(lastDirty).toBeNull()

    // Planning with the PREFILLED title: one create, form resets to the
    // prefill (never a blank form), guard cleared.
    const plannedSpy = vi.fn()
    tree = renderCard(plannedSpy)
    const form = required(findNode(tree, (n) => n.type === 'form'), 'Plan form')
    handler(form, 'onSubmit')?.({ preventDefault: () => undefined })
    await tick()
    tree = renderCard(plannedSpy)
    expect(api.createStudySession).toHaveBeenCalledTimes(1)
    expect(api.createStudySession.mock.calls[0]?.[0]).toEqual({
      title: 'Alpha',
      summary: undefined,
      context: { entityType: 'PROJECT_SECTION', referenceId: 's1' },
    })
    expect(plannedSpy).toHaveBeenCalledTimes(1)
    expect(lastDirty).toBeNull()
    expect(titleValue(), 'reset to prefill, not blank').toBe('Alpha')
  })
})
