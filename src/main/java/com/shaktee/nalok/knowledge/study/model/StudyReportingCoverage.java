package com.shaktee.nalok.knowledge.study.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Singleton marker for when interval-based period reporting became available
 * (the first transition that recorded an interval). Coverage resolves to the
 * earliest of this marker and the earliest persisted interval: intervals
 * predating the marker are real data and pull coverage back, while the
 * marker floors coverage against later interval deletion. Persisting the
 * roll-out instant gives reporting a stable "data begins here" statement.
 */
@Getter
@Entity
@Table(name = "studyreportingcoverage")
public class StudyReportingCoverage {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "coverage_start", nullable = false, updatable = false)
    private Instant coverageStart;

    protected StudyReportingCoverage() {
    }

    public StudyReportingCoverage(Instant coverageStart) {
        this.id = SINGLETON_ID;
        this.coverageStart = coverageStart;
    }
}
