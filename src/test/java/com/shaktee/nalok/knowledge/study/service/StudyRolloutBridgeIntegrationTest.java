package com.shaktee.nalok.knowledge.study.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.shaktee.nalok.knowledge.study.AdjustableClock;
import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.model.StudyWorkInterval;
import com.shaktee.nalok.knowledge.study.repository.StudySessionRepository;
import com.shaktee.nalok.knowledge.study.repository.StudyWorkIntervalRepository;

/**
 * Rollout-bridge semantics (D09 hardening): sessions that were already ACTIVE
 * when interval tracking first ran must not lose their ongoing leg.
 *
 * This class owns its own Spring context, and the test database is
 * create-drop per context — so every run starts from a pre-rollout state
 * (no intervals, no marker). Day-boundary coverage semantics live in their
 * own class ({@link StudyDayCoverageBoundaryIntegrationTest}) because both
 * scenarios need a fresh coverage anchor.
 */
@SpringBootTest
class StudyRolloutBridgeIntegrationTest {

    @Autowired
    private StudySessionService studySessionService;

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private StudyWorkIntervalRepository intervalRepository;

    @Autowired
    private AdjustableClock adjustableClock;

    @TestConfiguration
    static class BridgeClockConfig {

        @Bean
        @Primary
        AdjustableClock bridgeClockForTests() {
            return new AdjustableClock(Instant.now());
        }
    }

    @Test
    void preRolloutActiveLegIsBridgedAtFirstIntervalTransition() {
        // Fresh context database: no intervals and no marker yet — a genuine
        // pre-rollout state. Assert that assumption before acting on it.
        assertThat(intervalRepository.count()).isZero();
        assertThat(studySessionService.workIntervalCoverageStart()).isNull();

        // Pre-rollout ACTIVE session: model transition only — no service call,
        // so no interval is created and the marker does NOT appear.
        Instant legStart = zoned(2026, 10, 19, 10, 0);
        StudySession preRollout = studySessionRepository.save(
                new StudySession("Pre-rollout leg", null, null));
        preRollout.start(legStart);
        preRollout = studySessionRepository.saveAndFlush(preRollout);
        assertThat(intervalRepository.findBySessionId(preRollout.getId())).isEmpty();

        // The FIRST interval-maintaining transition: starting another session
        // auto-pauses the pre-rollout session (D06) and must BRIDGE its
        // untracked leg as a real interval [legStart, now].
        Instant transitionAt = zoned(2026, 10, 19, 12, 0);
        StudySession trigger = studySessionService.createSession("Post-rollout", null, null);
        adjustableClock.at(transitionAt, () ->
                studySessionService.startSession(trigger.getId()));

        List<StudyWorkInterval> bridged = intervalRepository.findBySessionId(preRollout.getId());
        assertThat(bridged).hasSize(1);
        assertThat(bridged.get(0).isOpen()).isFalse();
        assertThat(bridged.get(0).getStartedAt()).isEqualTo(legStart);
        assertThat(bridged.get(0).getEndedAt()).isEqualTo(transitionAt);

        // Coverage resolves to min(marker = noon, earliest interval = 10:00).
        assertThat(studySessionService.workIntervalCoverageStart()).isEqualTo(legStart);

        // The bridged leg is reportable through the normal overlap sums.
        long seconds = studySessionService.sumWorkSecondsOverlapping(
                preRollout.getId(), legStart, transitionAt.plusSeconds(1));
        assertThat(seconds).isEqualTo(Duration.between(legStart, transitionAt).getSeconds());

        // Bridging happens exactly ONCE: later transitions add only their own
        // legs and never duplicate the pre-rollout bridge.
        StudySession second = studySessionService.createSession("Post-rollout 2", null, null);
        adjustableClock.at(transitionAt.plusSeconds(60), () ->
                studySessionService.startSession(second.getId()));
        assertThat(intervalRepository.findBySessionId(preRollout.getId())).hasSize(1);
        assertThat(intervalRepository.findBySessionId(trigger.getId())).hasSize(1);
    }

    private static Instant zoned(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC).toInstant();
    }
}
