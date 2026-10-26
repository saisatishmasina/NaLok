package com.shaktee.nalok.knowledge.study.web;

import java.time.Instant;
import java.util.UUID;

import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.model.StudySessionStatus;
import com.shaktee.nalok.world.reference.EntityReference;
import com.shaktee.nalok.world.reference.EntityType;

/**
 * Bounded API representation of a study session. Never expose the JPA entity
 * directly. activeSecondsTotal is the authoritative accumulated work time,
 * extended live for the ACTIVE session so the UI can render the running
 * timer between refreshes.
 */
public record StudySessionDto(
        UUID id,
        String title,
        String summary,
        StudySessionStatus status,
        ContextDto context,
        Instant createdAt,
        Instant startedAt,
        Instant activeSince,
        Instant pausedAt,
        Instant completedAt,
        long activeSecondsTotal,
        Instant serverNow) {

    public record ContextDto(
            EntityType entityType,
            UUID referenceId) {

        static ContextDto of(EntityReference reference) {
            if (reference == null) {
                return null;
            }
            return new ContextDto(reference.getEntityType(), reference.getReferenceId());
        }
    }

    public static StudySessionDto of(StudySession session, Instant now) {
        long activeSecondsTotal = session.getActiveWorkTime() == null
                ? 0
                : session.getActiveWorkTime().getSeconds();
        if (session.getStatus() == StudySessionStatus.ACTIVE
                && session.getActiveSince() != null
                && now.isAfter(session.getActiveSince())) {
            activeSecondsTotal += java.time.Duration
                    .between(session.getActiveSince(), now).getSeconds();
        }

        return new StudySessionDto(
                session.getId(),
                session.getTitle(),
                session.getSummary(),
                session.getStatus(),
                ContextDto.of(session.getContext()),
                session.getCreatedAt(),
                session.getStartedAt(),
                session.getActiveSince(),
                session.getPausedAt(),
                session.getCompletedAt(),
                activeSecondsTotal,
                now);
    }
}
