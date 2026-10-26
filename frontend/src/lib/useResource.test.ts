import { describe, expect, it } from 'vitest'
import { elapsedSinceAnchor } from './useResource'

/*
 * The live-timer anchor must be skew-immune: elapsed time comes from the
 * client's monotonic clock between the anchor instant and now, never from
 * wall-clock arithmetic against serverNow. These tests pin the pure math;
 * the hook wires it to performance.now() ticks.
 */

const ANCHOR_PERF = 10_000 // ms on the client's monotonic clock

describe('elapsedSinceAnchor', () => {
  it('returns the server compounding exactly one second later', () => {
    expect(elapsedSinceAnchor(120, ANCHOR_PERF, ANCHOR_PERF + 1000))
      .toBe(121)
  })

  it('clamps a tick from before the anchor to zero growth', () => {
    // A re-anchored render raced the tick: never compute negative drift.
    expect(elapsedSinceAnchor(120, ANCHOR_PERF, ANCHOR_PERF - 500))
      .toBe(120)
  })

  it('is INDEPENDENT of what the server wall clock said (skew immunity)', () => {
    // The anchor carries the server TOTAL, not a server timestamp: a device
    // clock minutes ahead (or behind) cannot inject phantom minutes because
    // only monotonic deltas are added.
    const beforeAnchor = elapsedSinceAnchor(3600, ANCHOR_PERF, ANCHOR_PERF)
    const afterTwoMinutes = elapsedSinceAnchor(3600, ANCHOR_PERF, ANCHOR_PERF + 2 * 60 * 1000)
    expect(afterTwoMinutes - beforeAnchor).toBe(120)
  })

  it('floors partial seconds so the timer advances in whole numbers', () => {
    expect(elapsedSinceAnchor(120, ANCHOR_PERF, ANCHOR_PERF + 1999))
      .toBe(121)
  })

  it('never runs backwards between ticks', () => {
    const a = elapsedSinceAnchor(0, ANCHOR_PERF, ANCHOR_PERF + 5000)
    const b = elapsedSinceAnchor(0, ANCHOR_PERF, ANCHOR_PERF + 8000)
    expect(b).toBeGreaterThanOrEqual(a)
  })
})
