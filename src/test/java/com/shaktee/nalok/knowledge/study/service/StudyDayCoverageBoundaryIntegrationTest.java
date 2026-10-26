package com.shaktee.nalok.knowledge.study.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.shaktee.nalok.knowledge.study.AdjustableClock;
import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.repository.StudySessionRepository;

/**
 * Day-coverage boundary semantics (D09): a day whose end equals the coverage
 * instant has provably zero covered time and must stay uncovered — reporting
 * it as covered would fabricate a 0:00 day. Lives in its own class so its
 * fresh create-drop context starts with NO earlier coverage anchor: the
 * boundary scenario needs the global coverage start to be exactly the
 * boundary, which is impossible in a database where earlier test data pulls
 * coverage back.
 */
@SpringBootTest
class StudyDayCoverageBoundaryIntegrationTest {

    @Autowired
    private StudySessionService studySessionService;

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private AdjustableClock adjustableClock;

    @TestConfiguration
    static class BoundaryClockConfig {

        @Bean
        @Primary
        AdjustableClock boundaryClockForTests() {
            return new AdjustableClock(Instant.now());
        }
    }

    @Test
    void dayEndingExactlyAtItsCoverageInstantIsUncovered() {
        Instant boundary = zoned(2026, 10, 20, 0, 0); // == Oct 19's dayEnd
        StudySession preRollout = studySessionRepository.save(
                new StudySession("Boundary leg", null, null));
        preRollout.start(boundary);
        preRollout = studySessionRepository.saveAndFlush(preRollout);

        Instant transitionAt = boundary.plusSeconds(30 * 60);
        StudySession trigger = studySessionService.createSession("Boundary trigger", null, null);
        adjustableClock.at(transitionAt, () ->
                studySessionService.startSession(trigger.getId()));

        // Coverage = min(marker = 00:30, earliest interval = 00:00) = boundary.
        Instant coverage = studySessionService.workIntervalCoverageStart();
        assertThat(coverage).isEqualTo(boundary);

        // Close the trigger leg deterministically so the day bucket is exact.
        adjustableClock.at(transitionAt.plusSeconds(5 * 60), () ->
                studySessionService.pauseSession(trigger.getId()));

        // Park the clock inside the boundary week so weeklySummary reports it
        // (weeklySummary always reports the week containing "now").
        StudyWeeklySummary summary = adjustableClock.parked(
                zoned(2026, 10, 20, 12, 0), () ->
                        studySessionService.weeklySummary(ZoneId.of("UTC")));

        StudyWeeklySummary.DayBucket dayBefore = summary.days().stream()
                .filter(day -> day.date().equals(LocalDate.of(2026, 10, 19)))
                .findFirst().orElseThrow();
        assertThat(dayBefore.covered()).isFalse();
        assertThat(dayBefore.activeSeconds()).isEqualTo(0);

        StudyWeeklySummary.DayBucket dayOf = summary.days().stream()
                .filter(day -> day.date().equals(LocalDate.of(2026, 10, 20)))
                .findFirst().orElseThrow();
        assertThat(dayOf.covered()).isTrue();
        // Oct 20 holds the bridged leg (00:00–00:30) plus the trigger's exact
        // 5-minute leg (00:30–00:35). Nothing of either reaches Oct 19.
        assertThat(dayOf.activeSeconds()).isEqualTo(30 * 60 + 5 * 60);
    }

    private static Instant zoned(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC).toInstant();
    }
}
