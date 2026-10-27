import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  createStudySession,
  fetchCurrentStudySession,
  fetchProjectSections,
  fetchProjects,
  fetchStudyHistory,
  finishStudySession,
  pauseStudySession,
  resumeStudySession,
  startStudySession,
} from '../api/dashboard'
import type { Project, ProjectSection, StudySession } from '../api/types'
import { useLiveStudySeconds, useResource } from '../lib/useResource'
import { resolveCreateContext, readPreselection, type ContextKind, type PreselectedContext } from '../lib/createContext'
import { formatDuration, formatShortDate } from '../lib/format'

type LifecycleKind = 'start' | 'pause' | 'resume' | 'finish'

/**
 * Study workspace: focused (ACTIVE) session controls at the top, and
 * PLANNED/PAUSED sessions keep their lifecycle controls reachable in the
 * history list — pausing must never strand a session (resume/start stay
 * one click away). Refreshes happen in place via coordinated refetch()
 * calls; no page reloads, and a failed refresh keeps the last good data.
 */
export default function StudyPage() {
  const [historyPage, setHistoryPage] = useState(0)

  // F08 URL handoff: absorb ONCE into panel state (useState initializer) and
  // strip the params immediately — the picker never re-reads the URL, so a
  // later manual change or a full refresh cannot resurrect stale targets.
  const [searchParams, setSearchParams] = useSearchParams()
  const [preselected] = useState<PreselectedContext | null>(() => readPreselection(searchParams))
  useEffect(() => {
    if (preselected !== null && searchParams.toString() !== '') {
      setSearchParams({}, { replace: true })
    }
  }, [preselected, searchParams, setSearchParams])

  // Stable resource identities (Phase-1 fix): lifecycle actions and the
  // create flow refetch BOTH live resources in a coordinated way instead of
  // bumping a shared refresh key — a bumped key would recreate each
  // identity, clear the loaded rows, unmount forms mid-action and turn a
  // failed refresh into a blank panel.
  const current = useResource(fetchCurrentStudySession, [], 10_000)
  const history = useResource(
    () => fetchStudyHistory(historyPage, 20),
    [historyPage],
    10_000,
  )

  const handleAction = () => {
    current.refetch()
    history.refetch()
  }

  // History is newest-first: creating must land the new session on page 1.
  const handleCreated = () => {
    setHistoryPage(0)
    handleAction()
  }

  return (
    <div className="page">
      <header className="page-head">
        <h1>Study</h1>
        <p className="page-sub">One focused session at a time.</p>
      </header>

      <FocusedSessionCard
        current={current.data}
        loading={current.loading}
        error={current.error}
        onAction={handleAction}
      />

      <CreateSessionPanel preselected={preselected} onCreated={handleCreated} />

      <section className="glass-panel">
        <h2 className="card-title">Sessions</h2>
        {history.error !== null &&
          (history.data !== null ? (
            <div className="stale-note" role="status">
              Showing the last loaded history — a refresh failed just now:{' '}
              {history.error}
            </div>
          ) : (
            <div className="error-note">API error: {history.error}</div>
          ))}
        {history.loading && <div className="loading-note">Loading history…</div>}
        {history.data !== null &&
          (history.data.content.length === 0 ? (
            <div className="empty-note">
              No sessions yet — create one above.
            </div>
          ) : (
            <ul className="history-list">
              {history.data.content.map((session) => (
                <HistoryRow key={session.id} session={session} onAction={handleAction} />
              ))}
            </ul>
          ))}
        {history.data !== null && history.data.totalPages > 1 && (
          <div className="pager">
            <button
              type="button"
              disabled={historyPage === 0}
              onClick={() => setHistoryPage((n) => Math.max(0, n - 1))}
            >
              Back
            </button>
            <span className="pager-label">
              Page {history.data.page + 1} of {history.data.totalPages}
            </span>
            <button
              type="button"
              disabled={historyPage >= history.data.totalPages - 1}
              onClick={() => setHistoryPage((n) => n + 1)}
            >
              Next
            </button>
          </div>
        )}
      </section>
    </div>
  )
}

/** Lifecycle row for one session — reused by the Study history and the
 * section workbench's per-section list. Exported for reuse. */
export function HistoryRow({
  session,
  onAction,
}: {
  session: StudySession
  onAction: () => void
}) {
  const [pending, setPending] = useState<LifecycleKind | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  async function act(kind: LifecycleKind, label: string) {
    setPending(kind)
    setActionError(null)
    try {
      if (kind === 'start') {
        await startStudySession(session.id)
      } else if (kind === 'pause') {
        await pauseStudySession(session.id)
      } else if (kind === 'resume') {
        await resumeStudySession(session.id)
      } else {
        await finishStudySession(session.id)
      }
      onAction()
    } catch (cause) {
      setActionError(cause instanceof Error ? cause.message : `${label} failed`)
    } finally {
      setPending(null)
    }
  }

  const singleFocused = session.status === 'ACTIVE'

  return (
    <li className="history-item">
      <div className="history-main">
        <span
          className={`status-dot status-${session.status.toLowerCase()}`}
          aria-hidden="true"
        />
        <span className="history-title">{session.title}</span>
        {singleFocused && <span className="history-pulse">running</span>}
      </div>
      <div className="history-side">
        {actionError !== null && <span className="row-error">{actionError}</span>}
        <div className="history-actions">
          {session.status === 'PLANNED' && (
            <button
              type="button"
              className="action-btn mini primary"
              disabled={pending !== null}
              onClick={() => act('start', 'Start')}
            >
              Start
            </button>
          )}
          {session.status === 'PAUSED' && (
            <>
              <button
                type="button"
                className="action-btn mini primary"
                disabled={pending !== null}
                onClick={() => act('resume', 'Resume')}
              >
                Resume
              </button>
              <button
                type="button"
                className="action-btn mini finish"
                disabled={pending !== null}
                onClick={() => act('finish', 'Finish')}
              >
                Finish
              </button>
            </>
          )}
          {singleFocused && (
            <>
              <button
                type="button"
                className="action-btn mini"
                disabled={pending !== null}
                onClick={() => act('pause', 'Pause')}
              >
                Pause
              </button>
              <button
                type="button"
                className="action-btn mini finish"
                disabled={pending !== null}
                onClick={() => act('finish', 'Finish')}
              >
                Finish
              </button>
            </>
          )}
        </div>
        <div className="history-meta">
          {formatDuration(session.activeSecondsTotal)} ·{' '}
          {formatShortDate(session.createdAt)}
        </div>
      </div>
    </li>
  )
}

/**
 * The globally-focused session card is ALSO the section workbench's timer
 * (F08): one ACTIVE session exists globally, so this card is truthful on any
 * page it appears on. Exported for reuse.
 */
export function FocusedSessionCard({
  current,
  loading,
  error,
  onAction,
}: {
  current: StudySession | null
  loading: boolean
  error: string | null
  onAction: () => void
}) {
  const [pending, setPending] = useState<LifecycleKind | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const liveSeconds = useLiveStudySeconds(current)

  async function act(kind: LifecycleKind, id: string, label: string) {
    setPending(kind)
    setActionError(null)
    try {
      if (kind === 'start') {
        await startStudySession(id)
      } else if (kind === 'pause') {
        await pauseStudySession(id)
      } else if (kind === 'resume') {
        await resumeStudySession(id)
      } else {
        await finishStudySession(id)
      }
      onAction()
    } catch (cause) {
      setActionError(cause instanceof Error ? cause.message : `${label} failed`)
    } finally {
      setPending(null)
    }
  }

  return (
    <section className="glass-panel focus-panel">
      <h2 className="card-title">Focused session</h2>
      {error !== null && <div className="error-note">API error: {error}</div>}
      {!error && loading && (
        <div className="loading-note">Checking current session…</div>
      )}
      {!loading && error === null && current === null && (
        <div className="empty-note">
          Nothing is running. Paused or planned sessions can be resumed or
          started from the list below.
        </div>
      )}
      {current !== null && (
        <div className="focus-body">
          <div className="focus-title-row">
            <span className="status-dot status-active" aria-hidden="true" />
            <span className="focus-title">{current.title}</span>
          </div>
          <div className="focus-timer">
            {formatDuration(liveSeconds ?? current.activeSecondsTotal)}
          </div>
          {actionError !== null && <div className="error-note">{actionError}</div>}
          <div className="focus-actions">
            <button
              type="button"
              className="action-btn"
              disabled={pending !== null}
              onClick={() => act('pause', current.id, 'Pause')}
            >
              Pause
            </button>
            <button
              type="button"
              className="action-btn finish"
              disabled={pending !== null}
              onClick={() => act('finish', current.id, 'Finish')}
            >
              Finish
            </button>
          </div>
        </div>
      )}
    </section>
  )
}

/**
 * F05: create a PLANNED session from the UI. The context picker is optional
 * and dependent — choosing a project loads its sections server-side, and a
 * section can only be picked within a chosen project (a present context
 * reference requires BOTH type and id, mirroring EntityReference). Errors
 * surface from the ProblemDetail (title-too-long 400, dangling target 404).
 *
 * F08: a URL handoff may preselect the picker. The user's context KIND is
 * never reset on failure or on a missing linked target — the failure is
 * shown (Retry / link-note) so a flaky request or a stale link can never
 * silently turn an intended project/section context into a standalone
 * session (explicit-choice rule).
 */
export function CreateSessionPanel({
  preselected,
  onCreated,
}: {
  preselected: PreselectedContext | null
  onCreated: () => void
}) {
  const [title, setTitle] = useState('')
  const [summary, setSummary] = useState('')
  const [contextKind, setContextKind] = useState<ContextKind>(preselected?.kind ?? 'NONE')
  const [projects, setProjects] = useState<Project[] | null>(null)
  const [pickerError, setPickerError] = useState<string | null>(null)
  const [pickerTruncated, setPickerTruncated] = useState(false)
  const [projectFilter, setProjectFilter] = useState('')
  const [projectId, setProjectId] = useState(preselected?.projectId ?? '')
  const [sections, setSections] = useState<ProjectSection[] | null>(null)
  const [sectionId, setSectionId] = useState(preselected?.sectionId ?? '')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [linkNote, setLinkNote] = useState<string | null>(
    preselected !== null && preselected.projectId === ''
      ? 'The study link contained no project — choose one below; the context kind stays as linked.'
      : null,
  )

  // The picker only queries the API when it is actually opened. The user's
  // context KIND is never reset on failure — instead the error is shown with
  // a Retry control, so a flaky request cannot silently turn an intended
  // project/section context into a standalone session (explicit-choice rule).
  useEffect(() => {
    if (contextKind === 'NONE' || projects !== null) {
      return
    }
    let alive = true
    // Bounded multi-page load (newest-first, newest page 1): a personal
    // workspace will not exceed this often, and if it does we say so instead
    // of silently hiding projects.
    const maxPages = 5
    const pageSize = 100
    ;(async () => {
      const collected: Project[] = []
      for (let page = 0; page < maxPages; page += 1) {
        try {
          const envelope = await fetchProjects(undefined, page, pageSize)
          collected.push(...envelope.content)
          if (!alive) {
            return
          }
          if (envelope.totalPages <= page + 1) {
            setPickerTruncated(false)
            break
          }
          if (page === maxPages - 1) {
            setPickerTruncated(true)
          }
        } catch (cause) {
          if (!alive) {
            return
          }
          // Keep list state empty (select shows "No projects available") and
          // surface the failure with a Retry — never silently degrade context.
          setProjects([])
          setPickerError(
            cause instanceof Error ? cause.message : 'Could not load projects',
          )
          return
        }
      }
      if (alive) {
        setProjects(collected)
        setPickerError(null)
        // F08: a link-borne preselection is validated against what actually
        // loaded — a missing target warns BELOW, but the explicit kind and
        // chosen ids survive (no silent downgrade to a standalone session).
        if (preselected?.projectId !== '' && !collected.some((p) => p.id === preselected?.projectId)) {
          setLinkNote(
            'The linked project is not in the picker (deleted, archived, or beyond the newest 500). ' +
              'The context kind stays as linked — pick a project here, or switch back to "No context".',
          )
        }
      }
    })()
    return () => {
      alive = false
    }
  }, [contextKind, projects, preselected])

  // Dependent select: loads the sections of the chosen project.
  useEffect(() => {
    if (projectId === '') {
      return
    }
    let alive = true
    fetchProjectSections(projectId)
      .then((list) => {
        if (alive) {
          setSections(list)
          setError((previous) => (previous === null ? previous : null))
          // F08: likewise for a link-borne SECTION preselection — the ids
          // stay selected/required; the missing target becomes a note.
          if (
            preselected?.kind === 'PROJECT_SECTION' &&
            preselected.sectionId !== '' &&
            !list.some((s) => s.id === preselected.sectionId)
          ) {
            setLinkNote(
              'The linked section is not loadable (deleted or renamed?). ' +
                'The context kind stays PROJECT_SECTION — choose the right section, or switch back to "No context".',
            )
          }
        }
      })
      .catch((cause) => {
        if (alive) {
          setSections([])
          setError(cause instanceof Error ? cause.message : 'Could not load sections')
        }
      })
    return () => {
      alive = false
    }
  }, [projectId, preselected])

  const trimmedTitle = title.trim()
  const canSubmit = trimmedTitle !== '' && !pending
  const visibleProjects = (projects ?? []).filter((project) => {
    const needle = projectFilter.trim().toLowerCase()
    return needle === '' || project.title.toLowerCase().includes(needle)
  })

  function pickContext(kind: ContextKind) {
    // A manual change dissolves the URL handoff state entirely.
    setLinkNote(null)
    setContextKind(kind)
    setProjectId('')
    setSectionId('')
    setSections(null)
    setError(null)
    if (kind !== 'NONE') {
      setProjectFilter('')
    }
  }

  function retryPicker() {
    setPickerError(null)
    setProjects(null)
  }

  async function submit() {
    if (!canSubmit) {
      return
    }
    const resolution = resolveCreateContext(contextKind, projectId, sectionId)
    if (!resolution.ok) {
      setError(resolution.error)
      return
    }
    setPending(true)
    setError(null)
    try {
      const trimmedSummary = summary.trim()
      await createStudySession({
        title: trimmedTitle,
        summary: trimmedSummary === '' ? undefined : trimmedSummary,
        context: resolution.context,
      })
      setTitle('')
      setSummary('')
      setContextKind('NONE')
      setProjectId('')
      setSectionId('')
      setLinkNote(null)
      onCreated()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Creating the session failed')
    } finally {
      setPending(false)
    }
  }

  return (
    <section className="glass-panel create-panel">
      <h2 className="card-title">New session</h2>
      <form className="create-form" onSubmit={(event) => {
        event.preventDefault()
        void submit()
      }}>
        <div className="form-row">
          <label className="form-label" htmlFor="create-title">Title</label>
          <input
            id="create-title"
            className="form-input"
            value={title}
            maxLength={200}
            placeholder="What will you study?"
            onChange={(event) => setTitle(event.target.value)}
          />
        </div>
        <div className="form-row">
          <label className="form-label" htmlFor="create-summary">Summary</label>
          <input
            id="create-summary"
            className="form-input"
            value={summary}
            placeholder="Optional note for your future self"
            onChange={(event) => setSummary(event.target.value)}
          />
        </div>
        <div className="form-row">
          <label className="form-label" htmlFor="create-context">Context</label>
          <div className="form-controls">
            <select
              id="create-context"
              className="form-select"
              value={contextKind}
              onChange={(event) => pickContext(event.target.value as ContextKind)}
            >
              <option value="NONE">No context</option>
              <option value="PROJECT">Project…</option>
              <option value="PROJECT_SECTION">Section…</option>
            </select>
            {contextKind !== 'NONE' && projects !== null && projects.length > 25 && (
              <input
                className="form-input"
                aria-label="Filter projects"
                value={projectFilter}
                placeholder="Filter projects…"
                onChange={(event) => setProjectFilter(event.target.value)}
              />
            )}
            {contextKind !== 'NONE' && (
              <select
                className="form-select"
                aria-label="Project"
                value={projectId}
                onChange={(event) => {
                  // A new project invalidates any previously chosen section.
                  setProjectId(event.target.value)
                  setSectionId('')
                  setSections(null)
                }}
              >
                <option value="">
                  {projects === null
                    ? 'Loading…'
                    : visibleProjects.length === 0
                      ? 'No projects available'
                      : 'Choose a project'}
                </option>
                {visibleProjects.map((project) => (
                  <option key={project.id} value={project.id}>{project.title}</option>
                ))}
              </select>
            )}
            {contextKind === 'PROJECT_SECTION' && (
              <select
                className="form-select"
                aria-label="Section"
                value={sectionId}
                disabled={projectId === '' || sections === null || sections.length === 0}
                onChange={(event) => setSectionId(event.target.value)}
              >
                <option value="">
                  {projectId === ''
                    ? 'Choose a project first'
                    : sections === null
                      ? 'Loading…'
                      : sections.length === 0
                        ? 'No sections in this project'
                        : 'Choose a section'}
                </option>
                {sections?.map((section) => (
                  <option key={section.id} value={section.id}>{section.title}</option>
                ))}
              </select>
            )}
          </div>
        </div>
        {linkNote !== null && (
          <div className="stale-note" role="status">{linkNote}</div>
        )}
        {(pickerError !== null || pickerTruncated) && (
          <div className="picker-note">
            {pickerError !== null ? (
              <>
                <span>{pickerError}</span>
                <button type="button" className="action-btn mini" onClick={retryPicker}>
                  Retry
                </button>
              </>
            ) : (
              <span>
                Showing the newest 500 projects — use the filter or create from
                the Projects page first.
              </span>
            )}
          </div>
        )}
        {error !== null && <div className="error-note">{error}</div>}
        <div className="create-actions">
          <button
            type="submit"
            className="action-btn primary"
            disabled={!canSubmit}
          >
            {pending ? 'Creating…' : 'Create session'}
          </button>
        </div>
      </form>
    </section>
  )
}