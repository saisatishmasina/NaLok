package com.shaktee.nalok.knowledge.study;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Test clock: fixed instant plus a manual offset, so weekly-boundary tests
 * can place transitions at deterministic times instead of waiting.
 */
public final class AdjustableClock extends Clock {

    private Instant instant;

    public AdjustableClock(Instant initial) {
        this.instant = initial;
    }

    public void set(Instant instant) {
        this.instant = instant;
    }

    /**
     * Advances the clock to the given instant, running the supplied operation
     * with the clock parked there, then restores the previous instant.
     */
    public void at(Instant instant, Runnable operation) {
        Instant previous = this.instant;
        set(instant);
        try {
            operation.run();
        } finally {
            this.instant = previous;
        }
    }

    /**
     * Like {@link #at}, for operations that produce a value computed while
     * the clock is parked at the given instant.
     */
    public <T> T parked(Instant instant, java.util.function.Supplier<T> supplier) {
        Instant previous = this.instant;
        set(instant);
        try {
            return supplier.get();
        } finally {
            this.instant = previous;
        }
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("AdjustableClock is UTC-only");
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
