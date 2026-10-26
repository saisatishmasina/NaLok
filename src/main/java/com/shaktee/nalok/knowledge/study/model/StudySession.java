package com.shaktee.nalok.knowledge.study.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.shaktee.nalok.world.reference.EntityReference;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

@Getter
@Entity
@Table(name = "studysessions")
public class StudySession {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    /**
     * Optional context. Null = standalone session. Present = type AND id.
     */
    @Embedded
    private EntityReference context;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudySessionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    /**
     * When the current active leg began; null while not ACTIVE.
     */
    @Column(name = "active_since")
    private Instant activeSince;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * When the current leg was paused; non-null exactly while status is
     * PAUSED. Used to reject backward-dated resumes and completions.
     */
    @Column(name = "paused_at")
    private Instant pausedAt;

    /**
     * Accumulated active work time across all active legs.
     */
    @Column(name = "active_work_time", nullable = false)
    private Duration activeWorkTime;

    protected StudySession() {
    }

    public StudySession(String title, String summary, EntityReference context) {
        this.id = UUID.randomUUID();
        this.title = validateTitle(title);
        this.summary = summary;
        this.context = context;
        this.status = StudySessionStatus.PLANNED;
        this.createdAt = Instant.now();
        this.activeWorkTime = Duration.ZERO;
    }

    public void rename(String title) {
        this.title = validateTitle(title);
    }

    public void updateSummary(String summary) {
        this.summary = summary;
    }

    public void start(Instant now) {
        if (this.status != StudySessionStatus.PLANNED && this.status != StudySessionStatus.PAUSED) {
            throw new IllegalStateException("only planned or paused study sessions can be started");
        }

        if (this.startedAt == null) {
            this.startedAt = now;
        }

        this.status = StudySessionStatus.ACTIVE;
        this.activeSince = now;
    }

    public void pause(Instant now) {
        if (this.status != StudySessionStatus.ACTIVE) {
            throw new IllegalStateException("only active study sessions can be paused");
        }

        closeActiveLeg(now);
        this.pausedAt = now;
        this.status = StudySessionStatus.PAUSED;
    }

    public void resume(Instant now) {
        if (this.status != StudySessionStatus.PAUSED) {
            throw new IllegalStateException("only paused sessions can be resumed");
        }

        if (now.isBefore(this.pausedAt)) {
            throw new IllegalStateException("resume time is before the session was paused");
        }

        this.status = StudySessionStatus.ACTIVE;
        this.activeSince = now;
        this.pausedAt = null;
    }

    public void finish(Instant now) {
        if (this.status != StudySessionStatus.ACTIVE && this.status != StudySessionStatus.PAUSED) {
            throw new IllegalStateException("only active or paused sessions can be finished");
        }

        if (this.status == StudySessionStatus.ACTIVE) {
            closeActiveLeg(now);
        } else if (now.isBefore(this.pausedAt)) {
            throw new IllegalStateException(
                    "completion time is before the session was paused");
        }

        this.pausedAt = null;
        this.status = StudySessionStatus.COMPLETED;
        this.completedAt = now;
    }

    private void closeActiveLeg(Instant now) {
        if (now.isBefore(this.activeSince)) {
            throw new IllegalStateException(
                    "transition time is before the current active leg started");
        }

        this.activeWorkTime = this.activeWorkTime.plus(
                Duration.between(this.activeSince, now));
        this.activeSince = null;
    }

    private static String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Study session title cannot be empty");
        }

        String cleanedTitle = title.strip();

        if (cleanedTitle.length() > 200) {
            throw new IllegalArgumentException("Study session title cannot exceed 200 characters");
        }

        return cleanedTitle;
    }
}
