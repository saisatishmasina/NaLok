import { Fragment, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  createStudySession,
  fetchCurrentStudySession,
  fetchProjectSections,
  fetchStudySessionsByContext,
  updateSection,
} from '../api/dashboard'
import type { PageEnvelope, ProjectSection, StudySession } from '../api/types'
import { useResource, type ResourceState } from '../lib/useResource'
import { FocusedSessionCard, HistoryRow } from './Study'

/**
 * F08 section workbench (user-ordered flow change): diving into a section
 * opens ITS OWN page — details, in-place editing, session planning and the
 * running-timer card all live here. Sections of the same project are listed
 * below so hopping to another section is one click; nothing navigates away
 * to hand the context over. "Plan now, start manually": planning creates a
 * PLANNED session bound to this section; Start stays an explicit click.
 *
 * Reliability contract (Phase-1 fix): the three live resources below keep
 * STABLE identities. Plan/edit/lifecycle actions and the Refresh button run
 * coordinated refetch() calls instead of bumping a shared refresh key — a
 * bumped key would recreate each identity, clear the loaded data, unmount
 * open forms, and turn a failed refresh into a blank page. A failed refresh
 * keeps the last good data under an error note.
 */
export default function SectionWorkbenchPage() {
  const params = useParams()
  const projectId = params.projectId
  const sectionId = params.sectionId

  if (projectId === undefined || sectionId === undefined) {
    return (
      <div className="page">
        <div className="empty-note">Section id missing from the URL.</div>
        <Link className="back-link" to="/projects">← Projects</Link>
      </div>
    )
  }

  return <Workbench projectId={projectId} sectionId={sectionId} />
}

/**
 * NOT keyed by the section id: switching sections only swaps the URL
 * parameter, so the loaded project-sections list is REUSED (no re-fetch of
 * the whole project on every hop) and the stepper/sibling nav stay live from
 * the same list. Section-local state (edit form, plan form) resets on a hop
 * by keying those two subtrees with the section id — and a hop with an
 * unsaved draft asks first instead of silently discarding it.
 */
export function Workbench({ projectId, sectionId }: { projectId: string; sectionId: string }) {
  // STABLE identity: the project's sections belong to the whole page.
  // Editing the section and discarding a stale draft refetch in place.
  const sections = useResource(
    () => fetchProjectSections(projectId),
    [projectId],
  )
  const { refetch: refetchSections } = sections

  // Per-section sessions: identities change only when the target (or list
  // page) actually changes. Lifecycle actions and planning refetch.
  const [listPage, setListPage] = useState(0)
  const currentSession = useResource(fetchCurrentStudySession, [], 10_000)
  const sectionSessions = useResource(
    () => fetchStudySessionsByContext('PROJECT_SECTION', sectionId, listPage, 20),
    [sectionId, listPage],
    10_000,
  )

  const refreshSessions = () => {
    currentSession.refetch()
    sectionSessions.refetch()
  }
  // Planning lands the new PLANNED session on the list head (newest first).
  const planned = () => {
    setListPage(0)
    refreshSessions()
  }

  // Unsaved-edit hop guard: the key is the SECTION holding the open draft,
  // so a confirmed hop naturally deactivates its own stale guard without a
  // reset effect.
  const [editingDraft, setEditingDraft] = useState<string | null>(null)
  const [planningDraft, setPlanningDraft] = useState<string | null>(null)
  function hop(event: { preventDefault: () => void }) {
    const guarded = editingDraft === sectionId || planningDraft === sectionId
    if (
      guarded &&
      !window.confirm('This section has unsaved edits — leave without saving?')
    ) {
      event.preventDefault()
      return
    }
    setListPage(0)
  }

  // Navigate prev/next: local ordering over the SAME loaded list — no
  // refetch, no reload, swapping the URL param is enough.
  const ordered = sections.data ?? []
  const index = ordered.findIndex((section) => section.id === sectionId)
  const previous = index > 0 ? ordered[index - 1] : null
  const next = index >= 0 && index + 1 < ordered.length ? ordered[index + 1] : null
  const current = index >= 0 ? ordered[index] : null

  return (
    <div className="page">
      <Link className="back-link" to={`/projects/${projectId}`} onClick={hop}>
        ← Back to project
      </Link>

      {sections.error !== null && sections.data === null && (
        <div className="error-note page-level">API error: {sections.error}</div>
      )}
      {sections.error !== null && sections.data !== null && (
        <div className="stale-note" role="status">
          Showing the last loaded sections — a refresh failed just now: {sections.error}
        </div>
      )}
      {sections.loading && <div className="loading-note">Loading section…</div>}

      {current === null && !sections.loading && sections.data !== null && (
        <div className="error-note page-level">
          This section does not belong to the loaded project (stale link?).
        </div>
      )}

      {current !== null && (
        <>
          <Fragment key={sectionId}>
            {/* Keyed per section: a hop starts the edit and plan forms from
                the CURRENT section's data — no stale drafts carried across a
                dive that the user explicitly confirmed. */}
            <SectionHero
              section={current}
              onSaved={refetchSections}
              onDiscarded={refetchSections}
              onEditingChange={setEditingDraft}
            />

            <SectionStepper
              projectId={projectId}
              previous={previous}
              next={next}
              onHop={hop}
            />
            <StudyCard section={current} onPlanned={planned} onDirtyChange={setPlanningDraft} />
          </Fragment>
          <SiblingNav projectId={projectId} sections={ordered} currentId={sectionId} onHop={hop} />

          <SectionSessionsCard
            current={currentSession}
            list={sectionSessions}
            listPage={listPage}
            onAction={refreshSessions}
            onPage={setListPage}
          />
        </>
      )}
    </div>
  )
}

function SectionHero({
  section,
  onSaved,
  onDiscarded,
  onEditingChange,
}: {
  section: ProjectSection
  onSaved: () => void
  onDiscarded: () => void
  onEditingChange: (sectionId: string | null) => void
}) {
  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  // Same contract as every other edit here: the draft proves the snapshot
  // its fields were seeded from — a mid-edit refresh can never authorize
  // stale content.
  const [draftVersion, setDraftVersion] = useState<number | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const trimmedTitle = title.trim()
  const unchanged =
    trimmedTitle === section.title.trim() &&
    description.trim() === (section.description ?? '')
  const draftIsStale = draftVersion !== null && draftVersion !== section.version

  function openEditor() {
    setEditing(true)
    setTitle(section.title)
    setDescription(section.description ?? '')
    setDraftVersion(section.version)
    setError(null)
    onEditingChange(section.id)
  }

  function discardDraft() {
    setEditing(false)
    setDraftVersion(null)
    setError(null)
    // The persisted state changed under us — re-sync before another edit.
    onEditingChange(null)
    onDiscarded()
  }

  function cancel() {
    setEditing(false)
    setDraftVersion(null)
    setError(null)
    onEditingChange(null)
  }

  async function save() {
    if (trimmedTitle === '' || draftVersion === null || pending) {
      return
    }
    setPending(true)
    setError(null)
    try {
      const trimmedDescription = description.trim()
      await updateSection(section.id, {
        title: trimmedTitle,
        description: trimmedDescription === '' ? null : trimmedDescription,
        version: draftVersion,
      })
      setPending(false)
      setEditing(false)
      setDraftVersion(null)
      setError(null)
      onEditingChange(null)
      onSaved()
    } catch (cause) {
      setPending(false)
      setError(cause instanceof Error ? cause.message : 'Saving the section failed')
      // Re-sync the persisted state; the draft keeps its CAPTURED version
      // and stays open (still counting as the unsaved edit for the guard).
      onSaved()
    }
  }

  if (editing) {
    return (
      <section className="glass-panel">
        <h2 className="card-title">Edit section</h2>
        <form className="create-form" onSubmit={(event) => {
          event.preventDefault()
          void save()
        }}>
          <div className="form-row">
            <label className="form-label" htmlFor={`workbench-title-${section.id}`}>Title</label>
            <input
              id={`workbench-title-${section.id}`}
              className="form-input"
              value={title}
              maxLength={200}
              onChange={(event) => setTitle(event.target.value)}
            />
          </div>
          <div className="form-row">
            <label className="form-label" htmlFor={`workbench-desc-${section.id}`}>Description</label>
            <input
              id={`workbench-desc-${section.id}`}
              className="form-input"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>
          {error !== null && <div className="error-note">{error}</div>}
          {draftIsStale && (
            <>
              <div className="error-note">
                This section changed while you were editing — reload before
                retrying; Save stays disabled until then.
              </div>
              <div className="create-actions">
                <button type="button" className="action-btn mini" onClick={discardDraft}>
                  Discard draft &amp; reload
                </button>
              </div>
            </>
          )}
          <div className="create-actions">
            <button type="button" className="action-btn mini" onClick={cancel} disabled={pending}>
              Cancel
            </button>
            <button
              type="submit"
              className="action-btn mini primary"
              disabled={trimmedTitle === '' || pending || unchanged || draftIsStale}
            >
              {pending ? 'Saving…' : 'Save'}
            </button>
          </div>
        </form>
      </section>
    )
  }

  return (
    <section className="glass-panel">
      <div className="row-head">
        <div>
          <p className="page-sub">Section {section.position + 1}</p>
          <h1>{section.title}</h1>
          <p className="page-sub">{section.description ?? 'No description yet.'}</p>
        </div>
        <div className="head-side">
          <button type="button" className="action-btn" onClick={openEditor}>
            Edit section
          </button>
        </div>
      </div>
    </section>
  )
}

function SectionStepper({
  projectId,
  previous,
  next,
  onHop,
}: {
  projectId: string
  previous: ProjectSection | null
  next: ProjectSection | null
  onHop: (event: { preventDefault: () => void }) => void
}) {
  if (previous === null && next === null) {
    return null
  }
  return (
    <div className="stepper-row">
      {previous !== null ? (
        <Link
          className="action-btn mini"
          to={`/projects/${projectId}/sections/${previous.id}`}
          onClick={onHop}
        >
          ← {previous.title}
        </Link>
      ) : (
        <span />
      )}
      {next !== null ? (
        <Link
          className="action-btn mini"
          to={`/projects/${projectId}/sections/${next.id}`}
          onClick={onHop}
        >
          {next.title} →
        </Link>
      ) : (
        <span />
      )}
    </div>
  )
}

function SiblingNav({
  projectId,
  sections,
  currentId,
  onHop,
}: {
  projectId: string
  sections: ProjectSection[]
  currentId: string
  onHop: (event: { preventDefault: () => void }) => void
}) {
  // Sections are RELOADED (in place) when the list is stale — showing them
  // under the hero makes hopping between sections one click (the user's
  // explicit ask); the current one is marked rather than being a link.
  if (sections.length <= 1) {
    return null
  }
  return (
    <section className="glass-panel">
      <h2 className="card-title">Sections in this project</h2>
      <ul className="section-list">
        {sections.map((section) => (
          <li
            key={section.id}
            className={
              section.id === currentId ? 'section-item current' : 'section-item'
            }
          >
            <div className="section-main">
              <span className="section-title">{section.title}</span>
              {section.description !== null && (
                <span className="section-desc">{section.description}</span>
              )}
            </div>
            {section.id === currentId ? (
              <span className="status-chip">Open</span>
            ) : (
              <Link
                className="action-btn mini"
                to={`/projects/${projectId}/sections/${section.id}`}
                onClick={onHop}
              >
                Dive in
              </Link>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}

/**
 * Plan a session for THIS section. The title is PREFILLED from the section
 * (documented flow: "plan now" should mostly be a one-click confirm) and
 * stays freely editable. Typed-but-unplanned content is reported to the
 * workbench so a hop asks before discarding it.
 */
export function StudyCard({
  section,
  onPlanned,
  onDirtyChange,
}: {
  section: ProjectSection
  onPlanned: () => void
  onDirtyChange: (sectionId: string | null) => void
}) {
  const [title, setTitle] = useState(section.title)
  const [summary, setSummary] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [plannedNote, setPlannedNote] = useState<string | null>(null)

  function reportDraft(nextTitle: string, nextSummary: string) {
    const dirty = nextTitle !== section.title || nextSummary !== ''
    onDirtyChange(dirty ? section.id : null)
  }

  const trimmedTitle = title.trim()
  // PLANNED is the honest create state; starting stays an explicit action in
  // the sessions card below (an ACTIVE session auto-pauses the previous
  // one, so a surprise-start would be a state change the user didn't pick).
  const canPlan = trimmedTitle !== '' && !pending
  const pristine = title === section.title && summary === ''

  async function plan() {
    if (!canPlan) {
      return
    }
    setPending(true)
    setError(null)
    try {
      const trimmedSummary = summary.trim()
      await createStudySession({
        title: trimmedTitle,
        summary: trimmedSummary === '' ? undefined : trimmedSummary,
        context: { entityType: 'PROJECT_SECTION', referenceId: section.id },
      })
      setPending(false)
      setTitle(section.title)
      setSummary('')
      setPlannedNote('Planned — press Start in the list below when you begin.')
      onDirtyChange(null)
      onPlanned()
    } catch (cause) {
      setPending(false)
      setError(cause instanceof Error ? cause.message : 'Planning the session failed')
    }
  }

  return (
    <section className="glass-panel">
      <h2 className="card-title">Study this section</h2>
      <p className="page-sub">
        Plans a session bound to “{section.title}” — the running timer shows
        below once you start it.
      </p>
      <form className="create-form" onSubmit={(event) => {
        event.preventDefault()
        void plan()
      }}>
        <div className="form-row">
          <label className="form-label" htmlFor="plan-title">Session title</label>
          <input
            id="plan-title"
            className="form-input"
            value={title}
            maxLength={200}
            placeholder="What are you working on here?"
            onChange={(event) => {
              setTitle(event.target.value)
              reportDraft(event.target.value, summary)
            }}
          />
        </div>
        <div className="form-row">
          <label className="form-label" htmlFor="plan-summary">Summary</label>
          <input
            id="plan-summary"
            className="form-input"
            value={summary}
            placeholder="Optional note for your future self"
            onChange={(event) => {
              setSummary(event.target.value)
              reportDraft(title, event.target.value)
            }}
          />
        </div>
        <div className="form-controls">
          <span className="status-chip">
            Context locked: {section.title}
          </span>
          <button
            type="button"
            className="action-btn mini"
            disabled={pending || pristine}
            onClick={() => {
              // "Reset fields" restores the prefill, not a blank form.
              setTitle(section.title)
              setSummary('')
              setError(null)
              onDirtyChange(null)
            }}
          >
            Reset fields
          </button>
        </div>
        {plannedNote !== null && (
          <div className="success-note">{plannedNote}</div>
        )}
        {error !== null && <div className="error-note">{error}</div>}
        <div className="create-actions">
          <button
            type="submit"
            className="action-btn"
            disabled={!canPlan}
          >
            {pending ? 'Planning…' : 'Plan session'}
          </button>
        </div>
      </form>
    </section>
  )
}

/**
 * Section-scoped sessions. It OWNS no request of its own: both resources
 * (current + paginated list) live in the Workbench so planning, editing and
 * lifecycle actions can refetch them in a coordinated way without changing
 * any resource identity.
 */
export function SectionSessionsCard({
  current,
  list,
  listPage,
  onAction,
  onPage,
}: {
  current: ResourceState<StudySession | null>
  list: ResourceState<PageEnvelope<StudySession>>
  listPage: number
  onAction: () => void
  onPage: (page: number) => void
}) {
  return (
    <>
      <FocusedSessionCard
        current={current.data}
        loading={current.loading}
        error={current.error}
        onAction={onAction}
      />

      <section className="glass-panel">
        <div className="sections-head">
          <h2 className="card-title">Sessions for this section</h2>
          <button type="button" className="action-btn mini" onClick={onAction}>
            Refresh
          </button>
        </div>

        {list.error !== null &&
          (list.data !== null ? (
            <div className="stale-note" role="status">
              Showing the last loaded list — a refresh failed just now: {list.error}
            </div>
          ) : (
            <div className="error-note">API error: {list.error}</div>
          ))}
        {list.loading && <div className="loading-note">Loading sessions…</div>}
        {list.data !== null &&
          (list.data.content.length === 0 ? (
            <div className="empty-note">
              Nothing planned for this section yet — plan it above.
            </div>
          ) : (
            <ul className="history-list">
              {list.data.content.map((session) => (
                <HistoryRow key={session.id} session={session} onAction={onAction} />
              ))}
            </ul>
          ))}
        {/* Same pager contract as the Study history: older sessions stay
            reachable, so a stranded PLANNED/PAUSED row never loses its
            Start/Resume controls. */}
        {list.data !== null && list.data.totalPages > 1 && (
          <div className="pager">
            <button
              type="button"
              disabled={listPage === 0}
              onClick={() => onPage(listPage - 1)}
            >
              Back
            </button>
            <span className="pager-label">
              Page {list.data.page + 1} of {list.data.totalPages}
            </span>
            <button
              type="button"
              disabled={listPage >= list.data.totalPages - 1}
              onClick={() => onPage(listPage + 1)}
            >
              Next
            </button>
          </div>
        )}
      </section>
    </>
  )
}
