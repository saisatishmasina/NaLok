import { apiGet, apiGetOptional, apiPost } from './client'
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
