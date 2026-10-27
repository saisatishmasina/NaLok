import { describe, expect, it } from 'vitest'
import { resolveCreateContext } from './createContext'

/*
 * Context is optional but EXPLICIT: an incomplete target must be an error,
 * never a silently standalone session. These tests pin that contract.
 */

describe('resolveCreateContext', () => {
  it('resolves NONE to no context', () => {
    expect(resolveCreateContext('NONE', '', '')).toEqual({ ok: true })
    // Even stale selections under NONE stay standalone.
    expect(resolveCreateContext('NONE', 'p-1', 's-1')).toEqual({ ok: true })
  })

  it('requires a project under PROJECT context', () => {
    expect(resolveCreateContext('PROJECT', '', '')).toEqual({
      ok: false,
      error: expect.stringContaining('Choose a project'),
    })
  })

  it('projects a complete PROJECT context', () => {
    const result = resolveCreateContext('PROJECT', 'p-1', '')
    expect(result).toEqual({
      ok: true,
      context: { entityType: 'PROJECT', referenceId: 'p-1' },
    })
  })

  it('requires BOTH project and section under SECTION context', () => {
    expect(resolveCreateContext('PROJECT_SECTION', '', '')).toEqual({
      ok: false,
      error: expect.stringContaining('Choose a project and a section'),
    })
    expect(resolveCreateContext('PROJECT_SECTION', 'p-1', '')).toEqual({
      ok: false,
      error: expect.stringContaining('Choose a project and a section'),
    })
  })

  it('links the SECTION id by entityType, not the project id', () => {
    const result = resolveCreateContext('PROJECT_SECTION', 'p-1', 's-1')
    expect(result).toEqual({
      ok: true,
      context: { entityType: 'PROJECT_SECTION', referenceId: 's-1' },
    })
  })

  it('a section context never leaks the PROJECT id as the target', () => {
    const result = resolveCreateContext('PROJECT_SECTION', 'p-1', 's-1')
    if (result.ok && result.context !== undefined) {
      expect(result.context.referenceId).not.toBe('p-1')
    }
  })
})
