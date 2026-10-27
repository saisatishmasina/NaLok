import { Link } from 'react-router-dom'
import { useCallback, useState } from 'react'
import { createProject, fetchProjects } from '../api/dashboard'
import type { Project, ProjectStatus } from '../api/types'
import { useResource } from '../lib/useResource'
import { formatShortDate, formatTime } from '../lib/format'

const STATUS_FILTERS: Array<{ key: string; label: string }> = [
  { key: 'ACTIVE', label: 'Active' },
  { key: 'PLANNED', label: 'Planned' },
  { key: 'PAUSED', label: 'Paused' },
  { key: 'COMPLETED', label: 'Completed' },
  { key: 'ARCHIVED', label: 'Archived' },
  { key: '', label: 'All' },
]

export default function ProjectsPage() {
  const [filter, setFilter] = useState('ACTIVE')
  const [page, setPage] = useState(0)

  const list = useResource(
    () => fetchProjects(filter === '' ? undefined : filter, page, 20),
    [filter, page],
  )
  const { refetch } = list

  // A NEW project is PLANNED: land it where the user can SEE it — under the
  // Planned filter on page 1 — and force an explicit refetch so a successful
  // create always visibly updates the list, even when filter/page happen to
  // already be the destination (bumping to PLANNED moves PLANNED→PLANNED too).
  const handleCreated = useCallback(() => {
    setFilter('PLANNED')
    setPage(0)
    refetch()
  }, [refetch])

  const switchFilter = useCallback((key: string) => {
    setFilter(key)
    setPage(0)
  }, [])

  // Retained data stays visible even after a FAILED refresh: a stale page
  // with an honest note beats a blank grid. `null` here means "either still
  // loading, or nothing was ever loaded" — the two loading/error branches
  // below handle those.
  const envelope = !list.loading ? list.data : null

  return (
    <div className="page">
      <header className="page-head row-head">
        <div>
          <h1>Projects</h1>
          <p className="page-sub">Goals with outcomes, sections and a lifecycle.</p>
        </div>
      </header>

      <CreateProjectPanel onCreated={handleCreated} />

      <div className="filter-row">
        {STATUS_FILTERS.map((entry) => (
          <button
            key={entry.key}
            type="button"
            className={entry.key === filter ? 'filter-chip active' : 'filter-chip'}
            onClick={() => switchFilter(entry.key)}
          >
            {entry.label}
          </button>
        ))}
      </div>

      {list.error !== null &&
        (envelope !== null ? (
          <div className="stale-note" role="status">
            Showing the last loaded list — a refresh failed just now: {list.error}
          </div>
        ) : (
          <div className="error-note page-level">API error: {list.error}</div>
        ))}
      {list.loading && <div className="loading-note">Loading projects…</div>}

      {envelope !== null &&
        (envelope.content.length === 0 ? (
          <div className="empty-note">No projects under this filter yet.</div>
        ) : (
          <div className="project-grid">
            {envelope.content.map((project) => (
              <ProjectCard key={project.id} project={project} />
            ))}
          </div>
        ))}

      {envelope !== null && envelope.totalPages > 1 && (
        <div className="pager">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage((n) => Math.max(0, n - 1))}
          >
            Back
          </button>
          <span className="pager-label">
            Page {envelope.page + 1} of {envelope.totalPages}
          </span>
          <button
            type="button"
            disabled={page >= envelope.totalPages - 1}
            onClick={() => setPage((n) => n + 1)}
          >
            Next
          </button>
        </div>
      )}

      {FOOTER_NOTE !== null && <p className="page-foot-note">{FOOTER_NOTE}</p>}
    </div>
  )
}

const FOOTER_NOTE: string | null = null

/**
 * F06: create a PLANNED project from the UI. The backend does the validation
 * (title required, max 200 chars); ProblemDetail errors surface verbatim.
 * On success the parent resets its filter/page — see handleCreated.
 */
export function CreateProjectPanel({ onCreated }: { onCreated: () => void }) {
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [createdTitle, setCreatedTitle] = useState<string | null>(null)

  const trimmedTitle = title.trim()
  const canSubmit = trimmedTitle !== '' && !pending

  function openForm() {
    setOpen(true)
    setCreatedTitle(null)
  }

  function cancelForm() {
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
      const created = await createProject({
        title: trimmedTitle,
        description: trimmedDescription === '' ? undefined : trimmedDescription,
      })
      setPending(false)
      setOpen(false)
      setTitle('')
      setDescription('')
      setCreatedTitle(created.title)
      onCreated()
    } catch (cause) {
      setPending(false)
      setError(cause instanceof Error ? cause.message : 'Creating the project failed')
    }
  }

  return (
    <div className="create-project">
      {!open && (
        <div className="create-project-intro">
          <button type="button" className="action-btn primary" onClick={openForm}>
            New project
          </button>
          {createdTitle !== null && (
            <span className="success-note" role="status">
              Created “{createdTitle}” — shown under Planned.
            </span>
          )}
        </div>
      )}
      {open && (
        <section className="glass-panel create-project-panel">
          <h2 className="card-title">New project</h2>
          <form className="create-form" onSubmit={(event) => {
            event.preventDefault()
            void submit()
          }}>
            <div className="form-row">
              <label className="form-label" htmlFor="project-title">Title</label>
              <input
                id="project-title"
                className="form-input"
                value={title}
                maxLength={200}
                placeholder="What are you building?"
                onChange={(event) => setTitle(event.target.value)}
              />
            </div>
            <div className="form-row">
              <label className="form-label" htmlFor="project-description">Description</label>
              <input
                id="project-description"
                className="form-input"
                value={description}
                placeholder="Optional outcome or scope"
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>
            {error !== null && <div className="error-note">{error}</div>}
            <div className="create-actions">
              <button type="button" className="action-btn" onClick={cancelForm} disabled={pending}>
                Cancel
              </button>
              <button type="submit" className="action-btn primary" disabled={!canSubmit}>
                {pending ? 'Creating…' : 'Create project'}
              </button>
            </div>
          </form>
        </section>
      )}
    </div>
  )
}

function ProjectCard({ project }: { project: Project }) {
  return (
    <Link to={`/projects/${project.id}`} className="project-card row-link">
      <div className={statusChipClass(project.status)}>{project.status}</div>
      <h2 className="project-title">{project.title}</h2>
      <p className="project-desc">{project.description ?? 'No description yet.'}</p>
      <footer className="project-foot">
        <span>
          Created {formatShortDate(project.createdAt)} ·{' '}
          {formatTime(project.createdAt)}
        </span>
        {project.completedAt !== null && (
          <span>Completed {formatShortDate(project.completedAt)}</span>
        )}
      </footer>
    </Link>
  )
}

function statusChipClass(status: ProjectStatus): string {
  return `status-chip status-${status.toLowerCase()}`
}
