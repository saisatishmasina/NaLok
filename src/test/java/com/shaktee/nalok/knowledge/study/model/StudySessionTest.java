package com.shaktee.nalok.knowledge.study.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shaktee.nalok.world.reference.EntityReference;
import com.shaktee.nalok.world.reference.EntityType;

class StudySessionTest {

    private static final Instant T0 = Instant.parse("2026-10-26T10:00:00Z");

    @Test
    void newSessionStartsPlannedWithZeroWorkTime() {
        StudySession session = new StudySession("Deep work", "chapter 4", null);

        assertThat(session.getId()).isNotNull();
        assertThat(session.getTitle()).isEqualTo("Deep work");
        assertThat(session.getStatus()).isEqualTo(StudySessionStatus.PLANNED);
        assertThat(session.getCreatedAt()).isNotNull();
        assertThat(session.getStartedAt()).isNull();
        assertThat(session.getActiveSince()).isNull();
        assertThat(session.getCompletedAt()).isNull();
        assertThat(session.getActiveWorkTime()).isEqualTo(Duration.ZERO);
        assertThat(session.getContext()).isNull();
    }

    @Test
    void validatesTitle() {
        assertThatThrownBy(() -> new StudySession(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudySession("   ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudySession("x".repeat(201), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startSetsStartedAtAndActiveSince() {
        StudySession session = new StudySession("Deep work", null, null);

        session.start(T0);

        assertThat(session.getStatus()).isEqualTo(StudySessionStatus.ACTIVE);
        assertThat(session.getStartedAt()).isEqualTo(T0);
        assertThat(session.getActiveSince()).isEqualTo(T0);
    }

    @Test
    void startedAtSurvivesPauseAndResume() {
        StudySession session = new StudySession("Deep work", null, null);
        session.start(T0);
        session.pause(T0.plusSeconds(600));
        session.resume(T0.plusSeconds(1200));

        assertThat(session.getStartedAt()).isEqualTo(T0);
        assertThat(session.getActiveSince()).isEqualTo(T0.plusSeconds(1200));
    }

    @Test
    void startFromCompletedIsRejected() {
        StudySession session = new StudySession("Deep work", null, null);
        session.start(T0);
        session.finish(T0.plusSeconds(60));

        assertThatThrownBy(() -> session.start(T0))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getStatus()).isEqualTo(StudySessionStatus.COMPLETED);
    }

    @Test
    void pauseAccumulatesActiveWorkTime() {
        StudySession session = new StudySession("Deep work", null, null);
        session.start(T0);

        session.pause(T0.plusSeconds(1800));

        assertThat(session.getStatus()).isEqualTo(StudySessionStatus.PAUSED);
        assertThat(session.getActiveWorkTime()).isEqualTo(Duration.ofSeconds(1800));
        assertThat(session.getActiveSince()).isNull();
    }

    @Test
    void pauseWhenNotActiveIsRejected() {
        StudySession session = new StudySession("Deep work", null, null);

        assertThatThrownBy(() -> session.pause(T0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pauseBeforeActiveLegStartedIsRejected() {
        StudySession session = new StudySession("Deep work", null, null);
        session.start(T0);

        assertThatThrownBy(() -> session.pause(T0.minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getStatus()).isEqualTo(StudySessionStatus.ACTIVE);
        assertThat(session.getActiveWorkTime()).isEqualTo(Duration.ZERO);
    }

    @Test
    void multipleLegsAccumulate() {
        StudySession session = new StudySession("Deep work", null, null);
        session.start(T0);
        session.pause(T0.plusSeconds(1800)); // 30 min
        session.resume(T0.plusSeconds(3600));
        session.pause(T0.plusSeconds(4500)); // +15 min

        assertThat(session.getActiveWorkTime()).isEqualTo(Duration.ofSeconds(2700));
        assertThat(session.getStatus()).isEqualTo(StudySessionStatus.PAUSED);
    }

    @Test
    void resumeOnlyFromPaused() {
        StudySession session = new StudySession("Deep work", null, null);

        assertThatThrownBy(() -> session.resume(T0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resumeBeforePausedAtIsRejected() {
        StudySession session = new StudySession("Deep work", null, null);
        session.start(T0);
        session.pause(T0.plusSeconds(1800));

        assertThatThrownBy(() -> session.resume(T0.plusSeconds(1799)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getStatus()).isEqualTo(StudySessionStatus.PAUSED);
        assertThat(session.getActiveWorkTime()).isEqualTo(Duration.ofSeconds(1800));
        assertThat(session.getActiveSince()).isNull();
    }

    @Test
    void startFromPausedKeepsStartedAtAndValidatesPausedAt() {
        StudySession session = new StudySession("Deep work", null, null);
        session.start(T0);
        session.pause(T0.plusSeconds(1800));

        // Forward-dated restart behaves like resume.
        session.start(T0.plusSeconds(3600));
        assertThat(session.getStatus()).isEqualTo(StudySessionStatus.ACTIVE);
        assertThat(session.getStartedAt()).isEqualTo(T0);
        assertThat(session.getActiveSince()).isEqualTo(T0.plusSeconds(3600));

        // Backward-dated restart is rejected exactly like a backward resume.
        session.pause(T0.plusSeconds(5400));
        assertThatThrownBy(() -> session.start(T0.plusSeconds(4500)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getStatus()).isEqualTo(StudySessionStatus.PAUSED);
        assertThat(session.getActiveWorkTime()).isEqualTo(Duration.ofSeconds(3600));
    }

    @Test
    void finishBeforePausedAtIsRejected() {
        StudySession session = new StudySession("Deep work", null, null);
        session.start(T0);
        session.pause(T0.plusSeconds(1800));

        assertThatThrownBy(() -> session.finish(T0.plusSeconds(1799)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getStatus()).isEqualTo(StudySessionStatus.PAUSED);
        assertThat(session.getCompletedAt()).isNull();
        assertThat(session.getActiveWorkTime()).isEqualTo(Duration.ofSeconds(1800));
    }

    @Test
    void finishFromActiveClosesLegAndRecordsCompletion() {
        StudySession session = new StudySession("Deep work", null, null);
        session.start(T0);
        session.pause(T0.plusSeconds(1800));
        session.resume(T0.plusSeconds(3600));

        Instant finishAt = T0.plusSeconds(4500);
        session.finish(finishAt);

        assertThat(session.getStatus()).isEqualTo(StudySessionStatus.COMPLETED);
        assertThat(session.getCompletedAt()).isEqualTo(finishAt);
        assertThat(session.getActiveWorkTime()).isEqualTo(Duration.ofSeconds(2700));
        assertThat(session.getActiveSince()).isNull();
    }

    @Test
    void finishFromPausedKeepsAccumulatedTime() {
        StudySession session = new StudySession("Deep work", null, null);
        session.start(T0);
        session.pause(T0.plusSeconds(1800));

        Instant finishAt = T0.plusSeconds(7200); // paused in between
        session.finish(finishAt);

        assertThat(session.getStatus()).isEqualTo(StudySessionStatus.COMPLETED);
        assertThat(session.getCompletedAt()).isEqualTo(finishAt);
        assertThat(session.getActiveWorkTime()).isEqualTo(Duration.ofSeconds(1800));
    }

    @Test
    void finishFromPlannedIsRejected() {
        StudySession session = new StudySession("Deep work", null, null);

        assertThatThrownBy(() -> session.finish(T0))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getCompletedAt()).isNull();
    }

    @Test
    void contextIsStoredWhenPresent() {
        EntityReference context = EntityReference.of(
                EntityType.PROJECT, UUID.randomUUID());
        StudySession session = new StudySession("Deep work", null, context);

        assertThat(session.getContext()).isSameAs(context);
    }
}
