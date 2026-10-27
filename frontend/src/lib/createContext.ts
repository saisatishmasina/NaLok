import type { EntityType, StudyContext } from '../api/types'

export type ContextKind = 'NONE' | 'PROJECT' | 'PROJECT_SECTION'

export type ContextResolution =
  | { ok: true; context?: StudyContext }
  | { ok: false; error: string }

/**
 * F08: a one-shot context handoff arriving through the URL (see ProjectDetail
 * links). It is a REQUEST for a preselection, not a source of truth: the
 * panel absorbs it into picker state once and the params are stripped.
 */
export interface PreselectedContext {
  kind: Exclude<ContextKind, 'NONE'>
  projectId: string
  sectionId: string
}

/**
 * Strict parsing of the study-link handoff: anything that is not an explicit
 * PROJECT or PROJECT_SECTION link yields NO preselection, so a malformed
 * link can never tag a session with context the user did not see.
 */
export function readPreselection(params: URLSearchParams): PreselectedContext | null {
  const kindParam = params.get('context')
  if (kindParam !== 'PROJECT' && kindParam !== 'PROJECT_SECTION') {
    return null
  }
  return {
    kind: kindParam,
    projectId: params.get('project') ?? '',
    sectionId: kindParam === 'PROJECT_SECTION' ? (params.get('section') ?? '') : '',
  }
}

/**
 * Pure resolution of the picker state to the API context payload. Context is
 * OPTIONAL but must be EXPLICIT: choosing a context kind without completing
 * the target is an error — never a silent standalone session.
 */
export function resolveCreateContext(
  kind: ContextKind,
  projectId: string,
  sectionId: string,
): ContextResolution {
  if (kind === 'NONE') {
    return { ok: true }
  }
  if (kind === 'PROJECT') {
    if (projectId === '') {
      return {
        ok: false,
        error: 'Choose a project for the context, or switch back to "No context".',
      }
    }
    return { ok: true, context: { entityType: 'PROJECT', referenceId: projectId } }
  }
  // PROJECT_SECTION: BOTH targets are required as one explicit choice.
  if (projectId === '' || sectionId === '') {
    return {
      ok: false,
      error: 'Choose a project and a section for the context, or switch back to "No context".',
    }
  }
  const entityType: EntityType = 'PROJECT_SECTION'
  return { ok: true, context: { entityType, referenceId: sectionId } }
}
