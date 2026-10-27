/*
 * TypeScript mirrors of the backend DTOs (bounded shapes only — the UI never
 * sees more fields than the API controller publishes).
 */

export type EntityType = 'PROJECT' | 'PROJECT_SECTION'

export interface StudyContext {
  entityType: EntityType
  referenceId: string
}

/**
 * Mirrors StudySessionStatus — there is no INTERRUPTED state: interruption
 * recovery pauses the session (it never persists as a distinct status).
 */
export type StudyStatus = 'PLANNED' | 'ACTIVE' | 'PAUSED' | 'COMPLETED'

export interface StudySession {
  id: string
  title: string
  summary: string | null
  status: StudyStatus
  context: StudyContext | null
  createdAt: string
  startedAt: string | null
  activeSince: string | null
  pausedAt: string | null
  completedAt: string | null
  /** Accumulated work; live for the ACTIVE session between refreshes. */
  activeSecondsTotal: number
  /** Server instant used to compute live totals — the timer's anchor. */
  serverNow: string
}

export interface StudyDayBucket {
  date: string
  /**
   * False means "no reliable interval data for this day" (before the
   * rollout marker, or a future day). The UI must NOT render these as 0:00.
   */
  covered: boolean
  activeSeconds: number
}

export interface StudyWeeklySummary {
  zoneId: string
  weekStart: string
  weekEnd: string
  generatedAt: string
  coverageStart: string | null
  totalSeconds: number
  days: StudyDayBucket[]
}

export type ProjectStatus = 'PLANNED' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED'

export interface Project {
  id: string
  title: string
  description: string | null
  status: ProjectStatus
  createdAt: string
  completedAt: string | null
  /**
   * Concurrent-edit guard: the edit form sends this back; a mismatched
   * version is a 409 instead of a silent overwrite of newer work.
   */
  version: number
}

export interface ProjectSection {
  id: string
  projectId: string
  title: string
  description: string | null
  position: number
  /** Same concurrent-edit guard as Project (see above). */
  version: number
}

export interface ProjectSummary {
  total: number
  active: number
  paused: number
  completed: number
  archived: number
}

export interface PageEnvelope<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
