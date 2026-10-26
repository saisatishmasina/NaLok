package com.shaktee.nalok.knowledge.study.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One persisted active work leg of a study session: the wall-clock span from
 * start/resume to pause/finish. An interval with a null ended_at is still
 * open and belongs to the session's ACTIVE leg.
 *
 * Session totals (active_work_time) remain the authoritative completed total;
 * intervals exist for period reporting (weekly focus time) and cannot be
 * reconstructed from those totals, so they are persisted at every transition.
 */
@Getter
@Entity
@Table(name = "studyworkintervals", indexes = {
        @Index(name = "idx_studyworkintervals_session", columnList = "session_id")
})
public class StudyWorkInterval {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    /**
     * When the leg ended; null exactly while the interval is open.
     */
    @Column(name = "ended_at")
    private Instant endedAt;

    protected StudyWorkInterval() {
    }

    public StudyWorkInterval(UUID sessionId, Instant startedAt) {
        if (sessionId == null || startedAt == null) {
            throw new IllegalArgumentException("An interval requires session id AND start time");
        }

        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.startedAt = startedAt;
    }

    public boolean isOpen() {
        return this.endedAt == null;
    }

    public void close(Instant endedAt) {
        if (!isOpen()) {
            throw new IllegalStateException("study work interval is already closed");
        }

        if (endedAt.isBefore(this.startedAt)) {
            throw new IllegalStateException(
                    "interval end time is before the interval start time");
        }

        this.endedAt = endedAt;
    }
}
