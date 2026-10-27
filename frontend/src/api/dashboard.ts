import { apiGet, apiGetOptional, apiPost, apiPut } from './client'
import type {
  PageEnvelope,
  Project,
  ProjectSection,
  ProjectSummary,
  StudySession,
  StudyWeeklySummary,
} from './types'

/*
 * Endpoints used by the dashboard and current slices. Coverage limitation:
 * per-leg data begins at summary.coverageStart — the UI must present
 * uncovered days as "no reliable data", never as a fabricated zero.
 */

export function fetchProjectSummary(): Promise<ProjectSummary> {
  return apiGet('/api/projects/summary')
}

export function fetchProjects(
  status?: string,
  page = 0,
  size = 20,
): Promise<PageEnvelope<Project>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (status !== undefined) {
    params.set('status', status)
  }
  return apiGet(`/api/projects?${params.toString()}`)
}

export function fetchProject(id: string): Promise<Project> {
  return apiGet(`/api/projects/${id}`)
}

export function fetchProjectSections(id: string): Promise<ProjectSection[]> {
  return apiGet(`/api/projects/${id}/sections`)
}

/** POST → 201 Created with the persisted PLANNED project. */
export function createProject(payload: {
  title: string
  description?: string
}): Promise<Project> {
  return apiPost('/api/projects', payload)
}

/**
 * PUT → 200 with the updated project (one atomic edit intent). The payload
 * carries the version from the user's loaded snapshot: submitting against a
 * stale snapshot answers 409 rather than overwriting newer work.
 */
export function updateProject(
  id: string,
  payload: { title: string; description: string | null; version: number },
): Promise<Project> {
  return apiPut(`/api/projects/${id}`, payload)
}

/*
 * F07 lifecycle transitions. Each follows the model's own state rules;
 * rule violations surface as 409 ProblemDetail errors to the caller.
 */
export function startProject(id: string): Promise<Project> {
  return apiPost(`/api/projects/${id}/start`)
}

export function pauseProject(id: string): Promise<Project> {
  return apiPost(`/api/projects/${id}/pause`)
}

export function completeProject(id: string): Promise<Project> {
  return apiPost(`/api/projects/${id}/complete`)
}

export function archiveProject(id: string): Promise<Project> {
  return apiPost(`/api/projects/${id}/archive`)
}

/** POST → 201 with the section APPENDED after the last position. */
export function createSection(
  projectId: string,
  payload: { title: string; description?: string },
): Promise<ProjectSection> {
  return apiPost(`/api/projects/${projectId}/sections`, payload)
}

/**
 * PUT → 200 with the edited section (single atomic edit intent); stale
 * snapshot versions answer 409 instead of overwriting newer work.
 */
export function updateSection(
  sectionId: string,
  payload: { title: string; description: string | null; version: number },
): Promise<ProjectSection> {
  return apiPut(`/api/projects/sections/${sectionId}`, payload)
}

/** 204 when no session is ACTIVE — the UI needs that as a value, not an error. */
export function fetchCurrentStudySession(): Promise<StudySession | null> {
  return apiGetOptional('/api/study/sessions/current')
}

export function fetchStudyHistory(
  page = 0,
  size = 20,
): Promise<PageEnvelope<StudySession>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiGet(`/api/study/sessions?${params.toString()}`)
}

/**
 * F08 section workbench: sessions whose context references ONE target.
 * Unknown/an empty reference yields an EMPTY envelope (200), not an error.
 * Page/size params send the caller's list page — the backend caps size at
 * 100 — so older sessions stay reachable instead of being locked behind
 * page 0 forever.
 */
export function fetchStudySessionsByContext(
  entityType: 'PROJECT' | 'PROJECT_SECTION',
  referenceId: string,
  page = 0,
  size = 20,
): Promise<PageEnvelope<StudySession>> {
  const params = new URLSearchParams({
    entityType,
    referenceId,
    page: String(page),
    size: String(size),
  })
  return apiGet(`/api/study/sessions/by-context?${params.toString()}`)
}

/** The browser's zone is the honest default; the API defaults to UTC otherwise. */
export function fetchWeeklySummary(zone: string): Promise<StudyWeeklySummary> {
  return apiGet(`/api/study/summary?zone=${encodeURIComponent(zone)}`)
}

export function startStudySession(id: string): Promise<StudySession> {
  return apiPost(`/api/study/sessions/${id}/start`)
}

export function pauseStudySession(id: string): Promise<StudySession> {
  return apiPost(`/api/study/sessions/${id}/pause`)
}

export function finishStudySession(id: string): Promise<StudySession> {
  return apiPost(`/api/study/sessions/${id}/finish`)
}

export function resumeStudySession(id: string): Promise<StudySession> {
  return apiPost(`/api/study/sessions/${id}/resume`)
}

/** Shape mirrors CreateStudySessionRequest: absent context = standalone session. */
export interface CreateStudySessionPayload {
  title: string
  summary?: string
  context?: { entityType: 'PROJECT' | 'PROJECT_SECTION'; referenceId: string }
}

/** POST → 201 Created with the persisted PLANNED session. */
export function createStudySession(
  payload: CreateStudySessionPayload,
): Promise<StudySession> {
  return apiPost('/api/study/sessions', payload)
}