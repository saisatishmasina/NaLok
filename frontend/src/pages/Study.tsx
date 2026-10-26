import { useCallback, useState } from 'react'
import {
  fetchCurrentStudySession,
  fetchStudyHistory,
  finishStudySession,
  pauseStudySession,
  resumeStudySession,
  startStudySession,
} from '../api/dashboard'
import type { StudySession } from '../api/types'
import { useLiveStudySeconds, useResource } from '../lib/useResource'
import { formatDuration, formatShortDate } from '../lib/format'

type LifecycleKind = 'start' | 'pause' | 'resume' | 'finish'

/**
 * Study workspace: focused (ACTIVE) session controls at the top, and
 * PLANNED/PAUSED sessions keep their lifecycle controls reachable in the
 * history list — pausing must never strand a session (resume/start stay
 * one click away). Refreshes happen in place via a refresh key; no page
 * reloads.
 */
export default function StudyPage() {
  const [historyPage, setHistoryPage] = useState(0)
  const [refreshKey, setRefreshKey] = useState(0)

  const current = useResource(fetchCurrentStudySession, [refreshKey])
  const history = useResource(
    () => fetchStudyHistory(historyPage, 20),
    [historyPage, refreshKey],
  )

  const handleAction = useCallback(() => setRefreshKey((n) => n + 1), [])

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

      <section className="glass-panel">
        <h2 className="card-title">Sessions</h2>
        {history.error !== null && (
          <div className="error-note">API error: {history.error}</div>
        )}
        {history.loading && <div className="loading-note">Loading history…</div>}
        {history.data !== null &&
          (history.data.content.length === 0 ? (
            <div className="empty-note">
              No sessions yet — create one in a later slice.
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

function HistoryRow({
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

function FocusedSessionCard({
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
