import { describe, expect, it } from 'vitest'
import { formatDuration } from './format'

describe('formatDuration', () => {
  it('renders sub-minute values as seconds', () => {
    expect(formatDuration(0)).toBe('0s')
    expect(formatDuration(45)).toBe('45s')
  })

  it('renders whole minutes without hours', () => {
    expect(formatDuration(60)).toBe('1m')
    expect(formatDuration(45 * 60)).toBe('45m')
  })

  it('renders whole hours without leftover minutes', () => {
    expect(formatDuration(2 * 60 * 60)).toBe('2h')
  })

  it('splits hours and minutes', () => {
    expect(formatDuration(2 * 60 * 60 + 34 * 60)).toBe('2h 34m')
  })

  it('floors partial minutes in hour form (tabular stability)', () => {
    // 1h 59m 59s must not display as "2h 0m".
    expect(formatDuration(60 * 60 + 59 * 60 + 59)).toBe('1h 59m')
  })
})
