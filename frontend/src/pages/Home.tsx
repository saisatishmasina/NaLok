import DashboardCard from '../components/DashboardCard'
import { fetchCurrentStudySession, fetchProjectSummary, fetchWeeklySummary } from '../api/dashboard'
import type { ProjectSummary, StudySession, StudyWeeklySummary } from '../api/types'
import { browserZone, formatDuration, formatShortDate } from '../lib/format'
import { useLiveStudySeconds, useResource } from '../lib/useResource'
import { useState } from 'react'
import type { ReactNode, PropsWithChildren } from 'react'

export default function HomePage() {
  const projects = useResource(fetchProjectSummary, [])
  const current = useResource(fetchCurrentStudySession, [])
  const summary = useResource(() => fetchWeeklySummary(browserZone()), [])

  return (
    <div className="page">
      <header className="page-head">
        <h1>Home</h1>
        <p className="page-sub">Where things stand right now.</p>
      </header>

      <div className="cards">
        <ActiveProjectsCard
          state={projects}
          render={(summary) => (
            <>
              <div className="card-value">{summary.active}</div>
              <div className="card-sub">
                {summary.active === 1 ? 'project is active' : 'projects are active'}
              </div>
              <div className="chip-row">
                <Chip muted>{summary.paused} paused</Chip>
                <Chip muted>{summary.completed} completed</Chip>
                <Chip muted>{summary.archived} archived</Chip>
              </div>
            </>
          )}
        />
        <CurrentStudyCard state={current} />
        <WeekFocusCard state={summary} />
      </div>
    </div>
  )
}

/* ---------- Card 1: active projects ---------- */

function ActiveProjectsCard({
  state,
  render,
}: {
  state: ResourceProps<ProjectSummary>
  render: (summary: ProjectSummary) => ReactNode
}) {
  return (
    <DashboardCard title="Active projects" accent="mint" icon={<TargetIcon />}>
      <CardBody state={state} render={render} />
    </DashboardCard>
  )
}

/* ---------- Card 2: current study (live timer) ---------- */

function CurrentStudyCard({ state }: { state: ResourceProps<StudySession | null> }) {
  // Hook at component top level (rules of hooks): null while loading or
  // when nothing is running; real anchored seconds for an ACTIVE session.
  const liveSeconds = useLiveStudySeconds(state.data)

  return (
    <DashboardCard title="Current study" accent="orange" icon={<TimerIcon />}>
      <CardBody
        state={state}
        render={(session) =>
          session === null ? (
            <>
              <div className="card-value muted">Nothing running</div>
              <div className="card-sub">
                Start a session from Study when you're ready.
              </div>
            </>
          ) : (
            <>
              <div className="card-value" data-testid="live-timer">
                {formatDuration(liveSeconds ?? session.activeSecondsTotal)}
              </div>
              <div className="card-sub running-label">
                <span className="pulse-dot" aria-hidden="true" />
                {session.title}
              </div>
              <div className="card-sub">{session.summary ?? ''}</div>
            </>
          )
        }
      />
    </DashboardCard>
  )
}

/* ---------- Card 3: this week's focus ---------- */

function WeekFocusCard({ state }: { state: ResourceProps<StudyWeeklySummary> }) {
  return (
    <DashboardCard title="This week's focus" accent="yellow" icon={<PulseIcon />}>
      <CardBody
        state={state}
        render={(summary) => (
          <>
            <div className="card-value">{formatDuration(summary.totalSeconds)}</div>
            <div className="card-sub">
              since Monday in {summary.zoneId}
              {summary.coverageStart !== null && (
                <> · per-leg data since {formatShortDate(summary.coverageStart)}</>
              )}
            </div>
            <WeekBars summary={summary} />
            {summary.coverageStart === null && (
              <div className="coverage-note">
                No interval data yet — daily counts become available after your
                first session.
              </div>
            )}
          </>
        )}
      />
    </DashboardCard>
  )
}

function WeekBars({ summary }: { summary: StudyWeeklySummary }) {
  const activeDays = summary.days.filter((day) => day.covered)
  const max = Math.max(1, ...activeDays.map((day) => day.activeSeconds))
  // Resolved once per mount (day-rollover while mounted is irrelevant: the
  // summary refetches on navigation anyway).
  const [today] = useState(() => new Date().toLocaleDateString('sv'))

  return (
    <div className="week-bars" role="img" aria-label="Focus time per day this week">
      {summary.days.map((day) => (
        <div className="bar-slot" key={day.date}>
          {day.covered ? (
            <div
              className={`bar${day.date === today ? ' today' : ''}${
                day.activeSeconds === 0 ? ' empty' : ''
              }`}
              style={{ height: `${Math.max(4, (day.activeSeconds / max) * 100)}%` }}
              title={`${day.date}: ${formatDuration(day.activeSeconds)}`}
            />
          ) : (
            <div className="bar nodata" title={`${day.date}: no reliable data`} />
          )}
          <span className="bar-label">{day.date.slice(8)}</span>
        </div>
      ))}
    </div>
  )
}

/* ---------- shared small pieces ---------- */

interface ResourceProps<T> {
  data: T | null
  error: string | null
  loading: boolean
}

function CardBody<T>({
  state,
  render,
}: PropsWithChildren<{
  state: ResourceProps<T>
  render: (data: T) => ReactNode
}>) {
  if (state.error !== null) {
    return <ErrorNote error={state.error} />
  }
  if (state.loading) {
    return <Waiting />
  }
  // data === null is a legitimate VALUE for nullable endpoints (e.g. the
  // 204 "no current session") — the renderer below owns that case.
  return render(state.data as T)
}

function Waiting() {
  return <div className="card-value muted">…</div>
}

function ErrorNote({ error }: { error: string }) {
  return <div className="error-note">API error: {error}</div>
}

function Chip({ children }: PropsWithChildren<{ muted?: boolean }>) {
  return <span className="chip muted">{children}</span>
}

function TargetIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}>
      <circle cx="12" cy="12" r="8.2" />
      <circle cx="12" cy="12" r="4.4" />
      <circle cx="12" cy="12" r="1" fill="currentColor" stroke="none" />
    </svg>
  )
}

function TimerIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}>
      <circle cx="12" cy="13" r="7.2" />
      <path strokeLinecap="round" d="M12 9.8V13l2.4 1.6M9.4 3.5h5.2" />
    </svg>
  )
}

function PulseIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}>
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M3.5 12h4l2-5 4 10 2.5-5h4.5"
      />
    </svg>
  )
}
