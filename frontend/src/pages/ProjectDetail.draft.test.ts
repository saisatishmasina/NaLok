import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createFakeRuntime,
  findNode,
  handler,
  installFakeWindow,
  tick,
  type FakeHookNode,
} from '../test-support/fakeReact'

/*
 * White-box regression tests for the project-detail DRAFT CONTRACT, run
 * against the REAL ProjectHeader code through the minimal fake React
 * runtime. They pin the F07.1 review findings:
 *
 * 1. A draft submits the version CAPTURED when editing opened — never a
 *    version refreshed into props mid-edit (which would authorize stale
 *    content after a conflict-triggered re-sync).
 * 2. When the record changes under an open draft, Save is disabled until an
 *    explicit reload — no silent retry path.
 * 3. Reopening the editor starts from the CURRENT snapshot (a pre-save copy
 *    of the fields is never shown again, so a second open+save cannot
 *    resurrect stale text).
 */

const fake = createFakeRuntime()

const updateProjectMock = vi.fn(async (_id: string, payload: Record<string, unknown>) => payload)

beforeEach(() => {
  fake.reset()
  installFakeWindow()
  vi.doMock('react', () => fake.react)
  vi.doMock('react-router-dom', () => ({
    Link: () => null,
    useParams: () => ({}),
  }))
  vi.doMock('../api/dashboard', () => ({
    archiveProject: vi.fn(),
    completeProject: vi.fn(),
    createSection: vi.fn(),
    fetchProject: vi.fn(),
    fetchProjectSections: vi.fn(async () => []),
    pauseProject: vi.fn(),
    startProject: vi.fn(),
    updateProject: updateProjectMock,
    updateSection: vi.fn(),
  }))
})

afterEach(() => {
  vi.resetModules()
})

async function loadHeader() {
  const module = await import('./ProjectDetail')
  return module.ProjectHeader
}

interface DetailLike {
  id: string
  title: string
  description: string | null
  status: string
  completedAt: null
  version: number
}

function detail(version: number, title = 'Original'): DetailLike {
  return {
    id: 'p1',
    title,
    description: null,
    status: 'PLANNED',
    completedAt: null,
    version,
  }
}

function button(tree: unknown, label: string): FakeHookNode | undefined {
  return findNode(
    tree,
    (n) => n.type === 'button' && n.props.children === label,
  )
}

describe('ProjectHeader draft contract', () => {
  it('submits the version captured at draft-open, even when props refresh mid-edit', async () => {
    const ProjectHeader = await loadHeader()
    let current = detail(0)

    const renderOnce = () =>
      fake.render(() => ProjectHeader({ detail: current as never, onUpdated: () => undefined }))

    let tree = renderOnce()
    // Open the editor and start a draft against version 0.
    handler(button(tree, 'Edit details'))?.()
    tree = renderOnce()

    handler(findNode(tree, (n) => n.type === 'input' && n.props.id === 'edit-title'), 'onChange')?.(
      { target: { value: 'My draft' } },
    )

    // A concurrent save lands while the draft is open (props refresh to 1).
    current = detail(1, 'Concurrent change')
    tree = renderOnce()

    handler(findNode(tree, (n) => n.type === 'form'), 'onSubmit')?.({ preventDefault: () => {} })
    await tick()

    expect(updateProjectMock).toHaveBeenCalledTimes(1)
    const payload = updateProjectMock.mock.calls[0]?.[1] as { version?: number; title?: string }
    // THE contract: stale content is submitted ONLY with its own captured
    // version (0). A mid-refresh version bump (1) must not authorize it.
    expect(payload.version).toBe(0)
    expect(payload.title).toBe('My draft')
  })

  it('disables Save until an explicit reload once the record changed under the draft', async () => {
    const ProjectHeader = await loadHeader()
    let current = detail(0)

    const renderOnce = () =>
      fake.render(() => ProjectHeader({ detail: current as never, onUpdated: () => undefined }))

    let tree = renderOnce()
    handler(button(tree, 'Edit details'))?.()
    tree = renderOnce()
    handler(findNode(tree, (n) => n.type === 'input' && n.props.id === 'edit-title'), 'onChange')?.(
      { target: { value: 'My draft' } },
    )
    // The typed change only becomes visible after a re-render (event → render).
    tree = renderOnce()

    // Same version → editable and enabled.
    const saveButton = (): FakeHookNode | undefined => button(tree, 'Save changes')
    expect(saveButton()?.props.disabled).toBe(false)

    // Record changed under the open draft → Save must be disabled.
    current = detail(1, 'Concurrent change')
    tree = renderOnce()
    expect(saveButton()?.props.disabled).toBe(true)
  })

  it('reopens the editor from the CURRENT snapshot, never pre-save field copies', async () => {
    const ProjectHeader = await loadHeader()
    let current = detail(0)

    const renderOnce = () =>
      fake.render(() => ProjectHeader({ detail: current as never, onUpdated: () => undefined }))

    let tree = renderOnce()
    handler(button(tree, 'Edit details'))?.()
    tree = renderOnce()
    const titleInput = (): string | undefined =>
      findNode(tree, (n) => n.type === 'input' && n.props.id === 'edit-title')
        ?.props.value as string | undefined

    handler(findNode(tree, (n) => n.type === 'input' && n.props.id === 'edit-title'), 'onChange')?.(
      { target: { value: 'Draft edit' } },
    )
    tree = renderOnce()
    expect(titleInput()).toBe('Draft edit')

    // Close the draft WITHOUT saving (Cancel) — reopening must re-sync from
    // the current snapshot rather than keep or reuse the old field copy.
    handler(button(tree, 'Cancel'))?.()
    tree = renderOnce()

    // Reopen against the saved snapshot.
    current = detail(1, 'Saved title')
    await tick()
    tree = renderOnce()
    handler(button(tree, 'Edit details'))?.()
    tree = renderOnce()

    // The pre-save copy ("Draft edit") must be gone: the editor shows the
    // persisted snapshot a fresh edit is based on.
    expect(titleInput()).toBe('Saved title')
  })
})
