package com.shaktee.nalok.knowledge.study.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Weekly focus-time summary of persisted study work intervals, computed for a
 * caller-provided time zone with a Monday-based ISO week. Days before the
 * interval-coverage start are reported as not covered so the UI cannot
 * present a fabricated zero.
 */
public record StudyWeeklySummary(
        String zoneId,
        Instant weekStart,
        Instant weekEnd,
        Instant generatedAt,
        Instant coverageStart,
        long totalSeconds,
        List<DayBucket> days) {

    public record DayBucket(
            LocalDate date,
            boolean covered,
            long activeSeconds) {
    }
}
