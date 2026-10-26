import { Link } from 'react-router-dom'
import { useState } from 'react'
import { fetchProjects } from '../api/dashboard'
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

const READ_ONLY_NOTICE =
  'Projects are read-only in this slice — creating and editing arrive with the Projects UI task.'

export default function ProjectsPage() {
  const [filter, setFilter] = useState('ACTIVE')
  const [page, setPage] = useState(0)

  const list = useResource(
    () => fetchProjects(filter === '' ? undefined : filter, page, 20),
    [filter, page],
  )

  const envelope = list.error === null && !list.loading ? list.data : null

  return (
    <div className="page">
      <header className="page-head">
        <h1>Projects</h1>
        <p className="page-sub">{READ_ONLY_NOTICE}</p>
      </header>

      <div className="filter-row">
        {STATUS_FILTERS.map((entry) => (
          <button
            key={entry.key}
            type="button"
            className={entry.key === filter ? 'filter-chip active' : 'filter-chip'}
            onClick={() => {
              setFilter(entry.key)
              setPage(0)
            }}
          >
            {entry.label}
          </button>
        ))}
      </div>

      {list.error !== null && (
        <div className="error-note page-level">API error: {list.error}</div>
      )}
      {list.loading && <div className="loading-note">Loading projects…</div>}

      {envelope !== null &&
        (envelope.content.length === 0 ? (
          <div className="empty-note">No projects here yet.</div>
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
