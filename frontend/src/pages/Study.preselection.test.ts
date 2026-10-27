import { describe, expect, it } from 'vitest'
import { readPreselection } from '../lib/createContext'

/*
 * F08: the URL is a one-shot context handoff from the Projects page. Parsing
 * must be strict — anything that is not an explicit PROJECT or
 * PROJECT_SECTION handoff yields NO preselection, so a malformed link can
 * never accidentally create a context-tagged session without the user
 * seeing (and being able to change) the preselected kind.
 */
describe('readPreselection', () => {
  it('parses a project handoff', () => {
    expect(readPreselection(new URLSearchParams('context=PROJECT&project=p1'))).toEqual({
      kind: 'PROJECT',
      projectId: 'p1',
      sectionId: '',
    })
  })

  it('parses a section handoff with both ids', () => {
    expect(
      readPreselection(new URLSearchParams('context=PROJECT_SECTION&project=p1&section=s1')),
    ).toEqual({
      kind: 'PROJECT_SECTION',
      projectId: 'p1',
      sectionId: 's1',
    })
  })

  it('never invents a section id for a section handoff without one', () => {
    const parsed = readPreselection(new URLSearchParams('context=PROJECT_SECTION&project=p1'))
    expect(parsed?.sectionId).toBe('')
  })

  it('rejects unknown kinds as NO preselection', () => {
    expect(readPreselection(new URLSearchParams('context=NONE&project=p1'))).toBeNull()
    expect(readPreselection(new URLSearchParams())).toBeNull()
    expect(readPreselection(new URLSearchParams('context=ghost'))).toBeNull()
  })
})
