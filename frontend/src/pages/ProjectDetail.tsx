import { Link, useParams } from 'react-router-dom'
import { fetchProject, fetchProjectSections } from '../api/dashboard'
import { useResource } from '../lib/useResource'
import { formatShortDate, formatTime } from '../lib/format'

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

  if (project.error !== null) {
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

  const detail = project.data

  return (
    <div className="page">
      <BackToProjects />

      <header className="page-head row-head">
        <div>
          <h1>{detail.title}</h1>
          <p className="page-sub">{detail.description ?? 'No description yet.'}</p>
        </div>
        <div className={`status-chip status-${detail.status.toLowerCase()}`}>
          {detail.status}
        </div>
      </header>

      <div className="detail-meta">
        <span>
          Created {formatShortDate(detail.createdAt)} · {formatTime(detail.createdAt)}
        </span>
        {detail.completedAt !== null && (
          <span>Completed {formatShortDate(detail.completedAt)}</span>
        )}
      </div>

      <section className="sections-card">
        <h2 className="card-title">Sections</h2>
        {sections.error !== null && (
          <div className="error-note">API error: {sections.error}</div>
        )}
        {sections.loading && <div className="loading-note">Loading sections…</div>}
        {sections.data !== null &&
          (sections.data.length === 0 ? (
            <div className="empty-note">No sections yet.</div>
          ) : (
            <ul className="section-list">
              {sections.data.map((section) => (
                <li key={section.id} className="section-item">
                  {section.title}
                </li>
              ))}
            </ul>
          ))}
      </section>
    </div>
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
