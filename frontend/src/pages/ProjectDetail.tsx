import { Link, useParams } from 'react-router-dom'
import { useState } from 'react'
import {
  archiveProject,
  completeProject,
  createSection,
  fetchProject,
  fetchProjectSections,
  pauseProject,
  startProject,
  updateProject,
  updateSection,
} from '../api/dashboard'
import type { Project, ProjectSection, ProjectStatus } from '../api/types'
import { useResource } from '../lib/useResource'
import { formatShortDate, formatTime } from '../lib/format'

/**
 * Project detail (F06 edit + F07 lifecycle & sections).
 *
 * Lifecycle buttons are DERIVED from the persisted status — they exist only
 * for transitions the model actually allows, and rule violations still
 * surface as 409 ProblemDetail errors rather than being silently possible.
 * Archiving is terminal so it needs an explicit second click (confirmation).
 * Sections are listed position-ordered (API ordering), created APPENDED, and
 * edited as a single atomic intent.
 */
export default function ProjectDetailPage() {
  const params = useParams()
  const id = params.id

  const project = useResource(
    () => fetchProject(id ?? ''),
    // Route param drives reload on navigation between detail pages.
    [id],
  )
  const sections = useResource(
    () => fetchProjectSections(id ?? ''),
    [id],
  )

  if (id === undefined) {
    return <MissingProject />
  }

  // A failed refresh KEEPS the content visible under a stale-note; only a
  // resource that was never loaded shows the error/load card.
  if (project.error !== null && project.data === null) {
    return (
      <div className="page">
        <BackToProjects />
        <div className="error-note page-level">API error: {project.error}</div>
      </div>
    )
  }

  if (project.loading || project.data === null) {
    return (
      <div className="page">
        <BackToProjects />
        <div className="loading-note">Loading project…</div>
      </div>
    )
  }

  return (
    <div className="page">
      <BackToProjects />

      {project.error !== null && (
        <div className="stale-note" role="status">
          Showing the last loaded project — a refresh failed just now: {project.error}
        </div>
      )}

      <ProjectHeader detail={project.data} onUpdated={project.refetch} />

      <section className="sections-card">
        <SectionsCard
          projectId={id}
          sections={sections.data}
          loading={sections.loading}
          error={sections.error}
          onRefresh={sections.refetch}
        />
      </section>
    </div>
  )
}

/* ---------- Lifecycle + own-field edit ---------- */

type ProjectActionKind = 'start' | 'pause' | 'complete' | 'archive'

/** Only transitions the MODEL allows; ARCHIVED is terminal in the UI. */
const AVAILABLE_ACTIONS: Record<ProjectStatus, ProjectActionKind[]> = {
  PLANNED: ['start', 'archive'],
  ACTIVE: ['pause', 'complete', 'archive'],
  PAUSED: ['start', 'archive'],
  COMPLETED: ['archive'],
  ARCHIVED: [],
}

const ACTION_LABELS: Record<ProjectActionKind, string> = {
  start: 'Start',
  pause: 'Pause',
  complete: 'Mark complete',
  archive: 'Archive',
}

export function ProjectHeader({
  detail,
  onUpdated,
}: {
  detail: Project
  onUpdated: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  /**
   * The version the OPEN draft was based on, captured when editing STARTS —
   * never re-read from a later refresh. Submitting an old snapshot must be
   * IMPOSSIBLE after a conflict: the draft either still matches (same
   * version) or needs an explicit reload, so a refreshed version can never
   * silently authorize stale content.
   */
  const [draftVersion, setDraftVersion] = useState<number | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [actionKind, setActionKind] = useState<ProjectActionKind | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [archiveArmed, setArchiveArmed] = useState(false)

  const trimmedTitle = title.trim()
  const unchanged =
    trimmedTitle === detail.title.trim() &&
    description.trim() === (detail.description ?? '')
  const draftIsStale = draftVersion !== null && draftVersion !== detail.version

  function openEditor() {
    // Every edit opens from the CURRENT snapshot: a reopening form must
    // never show — or be based on — values from before the last save.
    setEditing(true)
    setTitle(detail.title)
    setDescription(detail.description ?? '')
    setDraftVersion(detail.version)
    setError(null)
  }

  function discardDraft() {
    // Explicit decision after a conflict: drop THIS draft, re-sync, and the
    // user re-edits from fresh data. A retry against the refreshed version
    // would authorize the stale content — never automated.
    setEditing(false)
    setDraftVersion(null)
    setError(null)
    onUpdated()
  }

  function cancel() {
    setEditing(false)
    setDraftVersion(null)
    setError(null)
  }

  async function save() {
    if (trimmedTitle === '' || draftVersion === null || pending) {
      return
    }
    setPending(true)
    setError(null)
    try {
      const trimmedDescription = description.trim()
      await updateProject(detail.id, {
        title: trimmedTitle,
        description: trimmedDescription === '' ? null : trimmedDescription,
        // The captured snapshot version — proves this edit was made against
        // the state the user actually saw when the form opened.
        version: draftVersion,
      })
      setPending(false)
      setEditing(false)
      setDraftVersion(null)
      setError(null)
      onUpdated()
    } catch (cause) {
      setPending(false)
      setError(cause instanceof Error ? cause.message : 'Saving the project failed')
      // Re-sync the persisted state so the page is truthful after a failure.
      // The draft stays with its CAPTURED version; if the record changed,
      // Save stays disabled until an explicit reload (see draftIsStale).
      onUpdated()
    }
  }

  async function runAction(kind: ProjectActionKind) {
    // ARCHIVE is terminal → requires a second, explicit click.
    if (kind === 'archive' && !archiveArmed) {
      setArchiveArmed(true)
      return
    }
    setArchiveArmed(false)
    setActionKind(kind)
    setActionError(null)
    try {
      if (kind === 'start') {
        await startProject(detail.id)
      } else if (kind === 'pause') {
        await pauseProject(detail.id)
      } else if (kind === 'complete') {
        await completeProject(detail.id)
      } else {
        await archiveProject(detail.id)
      }
      onUpdated()
    } catch (cause) {
      setActionError(cause instanceof Error ? cause.message : `${ACTION_LABELS[kind]} failed`)
      // Re-sync: a conflict/race (rare) should show the real persisted status.
      onUpdated()
    } finally {
      setActionKind(null)
    }
  }

  if (editing) {
    return (
      <section className="glass-panel edit-panel">
        <h2 className="card-title">Edit project</h2>
        <form className="create-form" onSubmit={(event) => {
          event.preventDefault()
          void save()
        }}>
          <div className="form-row">
            <label className="form-label" htmlFor="edit-title">Title</label>
            <input
              id="edit-title"
              className="form-input"
              value={title}
              maxLength={200}
              onChange={(event) => setTitle(event.target.value)}
            />
          </div>
          <div className="form-row">
            <label className="form-label" htmlFor="edit-description">Description</label>
            <input
              id="edit-description"
              className="form-input"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>
          {error !== null && <div className="error-note">{error}</div>}
          {draftIsStale && (
            <>
              <div className="error-note">
                Someone saved changes while you were editing — this draft is
                based on an older version. Reload before retrying; Save stays
                disabled until then.
              </div>
              <div className="create-actions">
                <button type="button" className="action-btn" onClick={discardDraft}>
                  Discard draft &amp; reload
                </button>
              </div>
            </>
          )}
          <div className="create-actions">
            <button type="button" className="action-btn" onClick={cancel} disabled={pending}>
              Cancel
            </button>
            <button
              type="submit"
              className="action-btn primary"
              disabled={trimmedTitle === '' || pending || unchanged || draftIsStale}
              title={unchanged ? 'Nothing changed yet' : undefined}
            >
              {pending ? 'Saving…' : 'Save changes'}
            </button>
          </div>
        </form>
      </section>
    )
  }

  const busy = actionKind !== null
  const actions = AVAILABLE_ACTIONS[detail.status]

  return (
    <>
      <header className="page-head row-head">
        <div>
          <h1>{detail.title}</h1>
          <p className="page-sub">{detail.description ?? 'No description yet.'}</p>
        </div>
        <div className="head-side">
          <span className={`status-chip status-${detail.status.toLowerCase()}`}>
            {detail.status}
          </span>
          {/* F08: context handoff into the Study workspace — the URL carries
              the preselection, the Study page consumes it and clears it. */}
          <Link
            className="action-btn"
            to={`/study?context=PROJECT&project=${detail.id}`}
          >
            Study this
          </Link>
          <button
            type="button"
            className="action-btn"
            onClick={openEditor}
            disabled={busy}
          >
            Edit details
          </button>
        </div>
      </header>

      {actions.length > 0 && (
        <div className="lifecycle-row">
          {actions.map((kind) => (
            <button
              key={kind}
              type="button"
              className={
                kind === 'archive' && archiveArmed
                  ? 'action-btn danger'
                  : 'action-btn'
              }
              disabled={busy}
              onClick={() => runAction(kind)}
            >
              {busy && actionKind === kind
                ? 'Working…'
                : kind === 'archive' && archiveArmed
                  ? 'Confirm archive'
                  : ACTION_LABELS[kind]}
            </button>
          ))}
          {archiveArmed && (
            <button
              type="button"
              className="action-btn mini"
              onClick={() => setArchiveArmed(false)}
            >
              Cancel
            </button>
          )}
          {actionError !== null && <span className="row-error">{actionError}</span>}
        </div>
      )}

      <div className="detail-meta">
        <span>
          Created {formatShortDate(detail.createdAt)} · {formatTime(detail.createdAt)}
        </span>
        {detail.completedAt !== null && (
          <span>Completed {formatShortDate(detail.completedAt)}</span>
        )}
      </div>
    </>
  )
}

/* ---------- Sections (F07) ---------- */

function SectionsCard({
  projectId,
  sections,
  loading,
  error,
  onRefresh,
}: {
  projectId: string
  sections: ProjectSection[] | null
  loading: boolean
  error: string | null
  onRefresh: () => void
}) {
  return (
    <>
      <HeaderWithAdd projectId={projectId} onRefresh={onRefresh} count={sections?.length ?? 0} />
      {error !== null &&
        (sections !== null ? (
          <div className="stale-note" role="status">
            Showing the last loaded sections — a refresh failed just now: {error}
          </div>
        ) : (
          <div className="error-note">API error: {error}</div>
        ))}
      {loading && <div className="loading-note">Loading sections…</div>}
      {sections !== null &&
        (sections.length === 0 ? (
          <div className="empty-note">No sections yet — add the first one above.</div>
        ) : (
          <ul className="section-list">
            {sections.map((section) => (
              <SectionRow key={section.id} section={section} onRefresh={onRefresh} />
            ))}
          </ul>
        ))}
    </>
  )
}

function HeaderWithAdd({
  projectId,
  onRefresh,
  count,
}: {
  projectId: string
  onRefresh: () => void
  count: number
}) {
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const trimmedTitle = title.trim()
  const canSubmit = trimmedTitle !== '' && !pending

  function closeForm() {
    setOpen(false)
    setTitle('')
    setDescription('')
    setError(null)
  }

  async function submit() {
    if (!canSubmit) {
      return
    }
    setPending(true)
    setError(null)
    try {
      const trimmedDescription = description.trim()
      await createSection(projectId, {
        title: trimmedTitle,
        description: trimmedDescription === '' ? undefined : trimmedDescription,
      })
      setPending(false)
      closeForm()
      onRefresh()
    } catch (cause) {
      setPending(false)
      setError(cause instanceof Error ? cause.message : 'Creating the section failed')
    }
  }

  return (
    <>
      <div className="sections-head">
        <h2 className="card-title">Sections{count > 0 ? ` (${count})` : ''}</h2>
        {!open && (
          <button type="button" className="action-btn mini primary" onClick={() => setOpen(true)}>
            Add section
          </button>
        )}
      </div>
      {open && (
        <form className="create-form section-add-form" onSubmit={(event) => {
          event.preventDefault()
          void submit()
        }}>
          <div className="form-row">
            <label className="form-label" htmlFor="section-title">Title</label>
            <input
              id="section-title"
              className="form-input"
              value={title}
              maxLength={200}
              placeholder="e.g. Setup"
              onChange={(event) => setTitle(event.target.value)}
            />
          </div>
          <div className="form-row">
            <label className="form-label" htmlFor="section-description">Description</label>
            <input
              id="section-description"
              className="form-input"
              value={description}
              placeholder="Optional"
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>
          {error !== null && <div className="error-note">{error}</div>}
          <div className="create-actions">
            <button type="button" className="action-btn mini" onClick={closeForm} disabled={pending}>
              Cancel
            </button>
            <button type="submit" className="action-btn mini primary" disabled={!canSubmit}>
              {pending ? 'Adding…' : 'Add section'}
            </button>
          </div>
        </form>
      )}
    </>
  )
}

function SectionRow({ section, onRefresh }: { section: ProjectSection; onRefresh: () => void }) {
  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  /**
   * Version captured when THIS editing draft opened; a later refresh may
   * show a newer version, but the draft keeps proving its own snapshot.
   */
  const [draftVersion, setDraftVersion] = useState<number | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const trimmedTitle = title.trim()
  const unchanged =
    trimmedTitle === section.title.trim() &&
    description.trim() === (section.description ?? '')
  const draftIsStale = draftVersion !== null && draftVersion !== section.version

  function openEditor() {
    // Every edit opens from the CURRENT row snapshot.
    setEditing(true)
    setTitle(section.title)
    setDescription(section.description ?? '')
    setDraftVersion(section.version)
    setError(null)
  }

  function discardDraft() {
    setEditing(false)
    setDraftVersion(null)
    setError(null)
    onRefresh()
  }

  function cancel() {
    setEditing(false)
    setDraftVersion(null)
    setError(null)
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
        // The captured snapshot version — proves this edit against the state
        // the user actually saw when the form opened.
        version: draftVersion,
      })
      setPending(false)
      setEditing(false)
      setDraftVersion(null)
      setError(null)
      onRefresh()
    } catch (cause) {
      setPending(false)
      setError(cause instanceof Error ? cause.message : 'Saving the section failed')
      // Re-sync the persisted state; the draft keeps its CAPTURED version.
      onRefresh()
    }
  }

  if (editing) {
    return (
      <li className="section-item editing">
        <form className="create-form" onSubmit={(event) => {
          event.preventDefault()
          void save()
        }}>
          <div className="form-row">
            <label className="form-label" htmlFor={`section-title-${section.id}`}>Title</label>
            <input
              id={`section-title-${section.id}`}
              className="form-input"
              value={title}
              maxLength={200}
              onChange={(event) => setTitle(event.target.value)}
            />
          </div>
          <div className="form-row">
            <label className="form-label" htmlFor={`section-desc-${section.id}`}>Description</label>
            <input
              id={`section-desc-${section.id}`}
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
      </li>
    )
  }

  return (
    <li className="section-item">
      <div className="section-main">
        <span className="section-title">{section.title}</span>
        {section.description !== null && (
          <span className="section-desc">{section.description}</span>
        )}
      </div>
      <div className="section-side">
        {/* F08: sections are WORKBENCHES — a row opens its own section page
            where studying, planning and the timer live (user-ordered flow). */}
        <Link
          className="action-btn mini"
          to={`/projects/${section.projectId}/sections/${section.id}`}
        >
          Open
        </Link>
        <button type="button" className="action-btn mini" onClick={openEditor}>
          Edit
        </button>
      </div>
    </li>
  )
}

function BackToProjects() {
  return (
    <Link to="/projects" className="back-link">
      ← Projects
    </Link>
  )
}

function MissingProject() {
  return (
    <div className="page">
      <div className="empty-note">Project id missing from the URL.</div>
      <BackToProjects />
    </div>
  )
}
