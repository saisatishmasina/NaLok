package com.shaktee.nalok.knowledge.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.shaktee.nalok.knowledge.study.AdjustableClock;
import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.model.StudySessionStatus;
import com.shaktee.nalok.knowledge.study.model.StudyWorkInterval;
import com.shaktee.nalok.knowledge.study.repository.StudyWorkIntervalRepository;

/**
 * Interval-based time reporting (work legs) backing weekly focus-time
 * summaries. Runs against the disposable MySQL test database.
 *
 * The injected clock is a test {@link AdjustableClock}, so transitions are
 * placed at deterministic instants — including across a weekly boundary and
 * a DST shift. Sum assertions are either per-session or per unique week, so
 * neighboring tests sharing the disposable database cannot contaminate them.
 */
@SpringBootTest
class StudyWorkIntervalIntegrationTest {

    @Autowired
    private StudySessionService studySessionService;

    @Autowired
    private StudyWorkIntervalRepository intervalRepository;

    @Autowired
    private AdjustableClock adjustableClock;

    @TestConfiguration
    static class AdjustableClockConfig {

        @Bean
        @Primary
        AdjustableClock adjustableClockForTests() {
            return new AdjustableClock(Instant.now());
        }
    }

    @Test
    void startAndPauseCloseExactlyOneIntervalWithMatchingTimestamps() {
        Instant mondayTen = zoned(2026, 10, 19, 10, 0);
        Instant mondayTwelve = zoned(2026, 10, 19, 12, 0);

        StudySession session = createStartedAt(mondayTen);
        adjustableClock.at(mondayTwelve, () -> studySessionService.pauseSession(session.getId()));

        List<StudyWorkInterval> intervals = intervals(session.getId());
        assertThat(intervals).hasSize(1);
        assertThat(intervals.get(0).isOpen()).isFalse();
        assertThat(intervals.get(0).getStartedAt()).isEqualTo(mondayTen);
        assertThat(intervals.get(0).getEndedAt()).isEqualTo(mondayTwelve);
        // Interval endpoints and session lifecycle timestamps come from the
        // very same captured instant.
        assertThat(intervals.get(0).getEndedAt())
                .isEqualTo(studySessionService.getSession(session.getId()).getPausedAt());

        long seconds = sessionSeconds(session.getId(),
                zoned(2026, 10, 19, 0, 0), zoned(2026, 10, 20, 0, 0));
        assertThat(seconds).isEqualTo(2 * 60 * 60);
    }

    @Test
    void resumeOpensSecondIntervalAndSumExcludesPausedTime() {
        Instant mondayTen = zoned(2026, 10, 19, 10, 0);
        Instant mondayTenThirty = zoned(2026, 10, 19, 10, 30);
        Instant wednesdayTwelve = zoned(2026, 10, 21, 12, 0);
        Instant wednesdayTwelveQuarter = zoned(2026, 10, 21, 12, 15);

        StudySession session = createStartedAt(mondayTen);
        adjustableClock.at(mondayTenThirty, () -> studySessionService.pauseSession(session.getId()));
        adjustableClock.at(wednesdayTwelve, () -> studySessionService.resumeSession(session.getId()));
        adjustableClock.at(wednesdayTwelveQuarter, () -> studySessionService.pauseSession(session.getId()));

        // Monday leg (30 min) + Wednesday leg (15 min); the paused gap
        // between the legs contributes nothing.
        List<StudyWorkInterval> intervals = intervals(session.getId());
        assertThat(intervals).hasSize(2);
        assertThat(intervals).allMatch(interval -> !interval.isOpen());

        long weekSeconds = sessionSeconds(session.getId(),
                zoned(2026, 10, 19, 0, 0), zoned(2026, 10, 26, 0, 0));
        assertThat(weekSeconds).isEqualTo((30 + 15) * 60);
    }

    @Test
    void finishWhileActiveClosesIntervalAndCountsCompleteLeg() {
        Instant mondayTen = zoned(2026, 10, 19, 10, 0);
        Instant mondayEleven = zoned(2026, 10, 19, 11, 0);

        StudySession session = createStartedAt(mondayTen);
        adjustableClock.at(mondayEleven, () -> studySessionService.finishSession(session.getId()));

        assertThat(studySessionService.getSession(session.getId()).getStatus())
                .isEqualTo(StudySessionStatus.COMPLETED);
        List<StudyWorkInterval> intervals = intervals(session.getId());
        assertThat(intervals).hasSize(1);
        assertThat(intervals.get(0).isOpen()).isFalse();

        long seconds = sessionSeconds(session.getId(),
                mondayTen, mondayEleven.plusSeconds(1));
        assertThat(seconds).isEqualTo(60 * 60);
    }

    @Test
    void finishingPausedSessionDoesNotAddIntervalTime() {
        Instant mondayTen = zoned(2026, 10, 19, 10, 0);
        Instant mondayTenHalf = zoned(2026, 10, 19, 10, 30);
        Instant mondayNoon = zoned(2026, 10, 19, 12, 0);

        StudySession session = createStartedAt(mondayTen);
        adjustableClock.at(mondayTenHalf, () -> studySessionService.pauseSession(session.getId()));
        adjustableClock.at(mondayNoon, () -> studySessionService.finishSession(session.getId()));

        List<StudyWorkInterval> intervals = intervals(session.getId());
        assertThat(intervals).hasSize(1);
        assertThat(intervals.get(0).isOpen()).isFalse();
        // Finishing from paused adds no work: the only leg ended at pause time.
        assertThat(intervals.get(0).getEndedAt()).isEqualTo(mondayTenHalf);

        long seconds = sessionSeconds(session.getId(),
                mondayTen, mondayNoon.plusSeconds(1));
        assertThat(seconds).isEqualTo(30 * 60);
    }

    @Test
    void autoPausingPreviousActiveSessionClosesItsInterval() {
        Instant mondayTen = zoned(2026, 10, 19, 10, 0);
        Instant mondayEleven = zoned(2026, 10, 19, 11, 0);

        StudySession first = createStartedAt(mondayTen);
        StudySession second = createStartedAt(mondayEleven);

        // Auto-pause (D06) ends the previous leg exactly when the new leg starts.
        List<StudyWorkInterval> firstIntervals = intervals(first.getId());
        assertThat(firstIntervals).hasSize(1);
        assertThat(firstIntervals.get(0).isOpen()).isFalse();
        assertThat(firstIntervals.get(0).getEndedAt()).isEqualTo(mondayEleven);

        List<StudyWorkInterval> secondIntervals = intervals(second.getId());
        assertThat(secondIntervals).hasSize(1);
        assertThat(secondIntervals.get(0).isOpen()).isTrue();
        assertThat(secondIntervals.get(0).getStartedAt()).isEqualTo(mondayEleven);

        // First session: exactly its one leg.
        long firstSeconds = sessionSeconds(first.getId(), mondayTen, mondayEleven.plusSeconds(1));
        assertThat(firstSeconds).isEqualTo(60 * 60);

        // The new session's open interval counts only up to the window end.
        long secondTruncated = sessionSeconds(second.getId(), mondayEleven,
                mondayEleven.plusSeconds(30 * 60));
        assertThat(secondTruncated).isEqualTo(30 * 60);

        // Leave no open legs behind: database hygiene for neighboring tests.
        adjustableClock.at(mondayEleven.plusSeconds(60 * 60),
                () -> studySessionService.pauseSession(second.getId()));
    }

    @Test
    void sessionCrossingWeeklyBoundarySplitsAcrossWindows() {
        Instant sundayQuarterToMidnight = zoned(2026, 10, 25, 23, 45);
        Instant mondayQuarterPastMidnight = zoned(2026, 10, 26, 0, 15);

        StudySession session = createStartedAt(sundayQuarterToMidnight);
        adjustableClock.at(mondayQuarterPastMidnight, () ->
                studySessionService.pauseSession(session.getId()));

        Instant weekSwitch = zoned(2026, 10, 26, 0, 0);
        long sundaySide = sessionSeconds(session.getId(),
                zoned(2026, 10, 19, 0, 0), weekSwitch);
        long mondaySide = sessionSeconds(session.getId(),
                weekSwitch, zoned(2026, 10, 27, 0, 0));

        // The 30-minute leg (23:45 Sunday to 00:15 Monday) splits exactly 15/15.
        assertThat(sundaySide).isEqualTo(15 * 60);
        assertThat(mondaySide).isEqualTo(15 * 60);
        List<StudyWorkInterval> intervals = intervals(session.getId());
        assertThat(intervals).hasSize(1);
        assertThat(intervals.get(0).getEndedAt()).isEqualTo(mondayQuarterPastMidnight);
    }

    @Test
    void degenerateAndCrossSessionWindowsBehaveSanely() {
        StudySession untouched = studySessionService.createSession("No intervals", null, null);

        // Inverted / empty window counts as zero, even for a session with data.
        StudySession withLegs = createStartedAt(zoned(2026, 10, 19, 10, 0));
        adjustableClock.at(zoned(2026, 10, 19, 10, 30),
                () -> studySessionService.pauseSession(withLegs.getId()));

        assertThat(sessionSeconds(withLegs.getId(),
                zoned(2026, 10, 19, 10, 30), zoned(2026, 10, 19, 10, 0))).isEqualTo(0);
        assertThat(sessionSeconds(withLegs.getId(),
                zoned(2026, 10, 19, 11, 0), zoned(2026, 10, 19, 11, 0))).isEqualTo(0);

        // A session-scoped window cannot leak another session's legs.
        assertThat(sessionSeconds(untouched.getId(),
                zoned(2026, 10, 19, 0, 0), zoned(2026, 10, 26, 0, 0))).isEqualTo(0);

        // The global sum for a window is at least the per-session sum inside it.
        long global = studySessionService.sumWorkSecondsOverlapping(
                zoned(2026, 10, 19, 0, 0), zoned(2026, 10, 26, 0, 0));
        long perSession = sessionSeconds(withLegs.getId(),
                zoned(2026, 10, 19, 0, 0), zoned(2026, 10, 26, 0, 0));
        assertThat(global).isGreaterThanOrEqualTo(perSession);
    }

    @Test
    void reportingCoverageStartTracksOldestInterval() {
        Instant coverageBefore = studySessionService.workIntervalCoverageStart();

        StudySession session = createStartedAt(zoned(2026, 10, 19, 10, 0));
        adjustableClock.at(zoned(2026, 10, 19, 10, 5),
                () -> studySessionService.pauseSession(session.getId()));

        Instant coverageAfter = studySessionService.workIntervalCoverageStart();
        assertThat(coverageAfter).isNotNull();
        assertThat(coverageAfter).isBeforeOrEqualTo(session.getStartedAt());

        // Coverage never shrinks as more legs accumulate.
        if (coverageBefore != null) {
            assertThat(coverageAfter).isBeforeOrEqualTo(coverageBefore);
        }
    }

    @Test
    void recoverInterruptedClosesOpenIntervalAtRecoveryInstant() {
        // Session started, then the app crashes with the leg still open.
        Instant mondayTen = zoned(2026, 10, 19, 10, 0);
        StudySession crashed = createStartedAt(mondayTen);
        assertThat(intervals(crashed.getId()).get(0).isOpen()).isTrue();

        // Days later the user invokes explicit recovery (D07): the open leg
        // is closed AT THE RECOVERY TIME. Counting the crash gap as active
        // work is a documented limitation — asserted exactly here.
        Instant recoveryInstant = zoned(2026, 10, 21, 9, 0);
        adjustableClock.at(recoveryInstant, () ->
                studySessionService.recoverInterrupted(crashed.getId()));

        List<StudyWorkInterval> intervals = intervals(crashed.getId());
        assertThat(intervals).hasSize(1);
        assertThat(intervals.get(0).isOpen()).isFalse();
        assertThat(intervals.get(0).getEndedAt()).isEqualTo(recoveryInstant);
        assertThat(studySessionService.getSession(crashed.getId()).getStatus())
                .isEqualTo(StudySessionStatus.PAUSED);

        long mondayToThursday = sessionSeconds(crashed.getId(),
                zoned(2026, 10, 19, 0, 0), zoned(2026, 10, 22, 0, 0));
        assertThat(mondayToThursday)
                .isEqualTo(Duration.between(mondayTen, recoveryInstant).getSeconds());
    }

    @Test
    void failedResumeRollsBackSessionAndIntervalChangesTogether() {
        // Richer construction so the failure happens AFTER interval writes:
        // A is paused-at-noon; F is ACTIVE with a leg that started at 10:30.
        // Resuming A at 11:00 (before its own paused_at) pauses F first —
        // F.pause() succeeds and its open interval is closed — and THEN fails
        // in A.resume(); the rollback must undo F's pause AND the interval
        // close together.
        Instant aStart = zoned(2026, 10, 19, 10, 0);
        StudySession a = createStartedAt(aStart);

        StudySession b = studySessionService.createSession("Paused future", null, null);
        adjustableClock.at(aStart.plusSeconds(2 * 60 * 60), () ->
                studySessionService.startSession(b.getId())); // auto-pauses A at noon
        adjustableClock.at(aStart.plusSeconds(3 * 60 * 60), () ->
                studySessionService.pauseSession(b.getId()));
        StudySession bBefore = studySessionService.getSession(b.getId());

        // F: created after, leg started earlier (clock parked backwards) so a
        // pause at 11:00 is VALID for this leg.
        StudySession f = studySessionService.createSession("F active", null, null);
        adjustableClock.at(aStart.plusSeconds(30 * 60), () ->
                studySessionService.startSession(f.getId()));
        assertThat(studySessionService.getSession(f.getId()).getStatus())
                .isEqualTo(StudySessionStatus.ACTIVE);
        List<StudyWorkInterval> fIntervalsBefore = intervals(f.getId());
        assertThat(fIntervalsBefore).hasSize(1);
        assertThat(fIntervalsBefore.get(0).isOpen()).isTrue();

        // The doomed resume: pauses F (leg close writes happen NOW, before
        // the failure), then A.resume rejects the back-dated time. Must land
        // strictly AFTER F's leg start (10:30) so F.pause succeeds, and
        // strictly BEFORE A.pausedAt (12:00) so A.resume throws.
        Instant doomedResumeAt = aStart.plusSeconds(90 * 60);
        assertThatThrownBy(() -> adjustableClock.at(doomedResumeAt, () ->
                studySessionService.resumeSession(a.getId())))
                .isInstanceOf(IllegalStateException.class);

        // F's session state AND its interval close must be rolled back.
        StudySession fAfter = studySessionService.getSession(f.getId());
        assertThat(fAfter.getStatus()).isEqualTo(StudySessionStatus.ACTIVE);
        assertThat(fAfter.getPausedAt()).isNull();
        List<StudyWorkInterval> fIntervalsAfter = intervals(f.getId());
        assertThat(fIntervalsAfter).hasSameSizeAs(fIntervalsBefore);
        assertThat(fIntervalsAfter.get(0).isOpen()).isTrue();

        // A and B remain exactly as they were.
        StudySession aAfter = studySessionService.getSession(a.getId());
        assertThat(aAfter.getStatus()).isEqualTo(StudySessionStatus.PAUSED);
        StudySession bAfter = studySessionService.getSession(b.getId());
        assertThat(bAfter.getStatus()).isEqualTo(StudySessionStatus.PAUSED);
        assertThat(bAfter.getPausedAt()).isEqualTo(bBefore.getPausedAt());

        // Database hygiene: clear the open leg.
        adjustableClock.at(bAfter.getPausedAt().plusSeconds(60 * 60), () ->
                studySessionService.pauseSession(f.getId()));
    }

    @Test
    void weeklySummaryProducesSevenExactDailyBuckets() {
        // Legs placed in the week of Monday 2026-06-01 (UTC); no other test
        // writes there, so the summary for that week is deterministic.
        Instant tuesdayTen = zoned(2026, 6, 2, 10, 0);
        Instant tuesdayEleven = zoned(2026, 6, 2, 11, 0);
        Instant thursdayTwelve = zoned(2026, 6, 4, 12, 0);
        Instant thursdayTwelveHalf = zoned(2026, 6, 4, 12, 30);

        StudySession session = createStartedAt(tuesdayTen);
        adjustableClock.at(tuesdayEleven, () -> studySessionService.pauseSession(session.getId()));
        adjustableClock.at(thursdayTwelve, () -> studySessionService.resumeSession(session.getId()));
        adjustableClock.at(thursdayTwelveHalf, () -> studySessionService.pauseSession(session.getId()));

        // Park the clock inside the same week as the legs: weeklySummary
        // always reports the week containing now.
        StudyWeeklySummary summary = adjustableClock.parked(thursdayTwelveHalf, () ->
                studySessionService.weeklySummary(ZoneId.of("UTC")));

        assertThat(summary.zoneId()).isEqualTo("UTC");
        assertThat(summary.weekStart()).isEqualTo(zoned(2026, 6, 1, 0, 0));
        assertThat(summary.weekEnd()).isEqualTo(zoned(2026, 6, 8, 0, 0));
        assertThat(summary.days()).hasSize(7);
        assertThat(summary.days().get(0).date()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(summary.days().get(6).date()).isEqualTo(LocalDate.of(2026, 6, 7));

        List<Long> daySeconds = summary.days().stream()
                .map(StudyWeeklySummary.DayBucket::activeSeconds)
                .toList();
        assertThat(daySeconds).containsExactly(0L, 3600L, 0L, 1800L, 0L, 0L, 0L);
        assertThat(summary.totalSeconds()).isEqualTo(5400L);
        // Any day that reports activity must be a covered day.
        assertThat(summary.days())
                .allSatisfy(day -> {
                    if (day.activeSeconds() > 0) {
                        assertThat(day.covered()).isTrue();
                    }
                });
    }

    @Test
    void weeklySummaryHandlesDstShiftedWindows() {
        // America/Chicago springs forward on Sunday 2026-03-08 (02:00 local
        // does not exist). A leg runs Sat 23:00 -> Sun 03:00 LOCAL: 3h real
        // (2026-03-08 05:00Z to 08:00Z, because 01:00-02:00 local is skipped).
        // It must split Sat 1h / Sun 2h without doubling or losing time.
        ZoneId chicago = ZoneId.of("America/Chicago");
        Instant legStart = LocalDateTime.of(2026, 3, 7, 23, 0).atZone(chicago).toInstant();
        Instant legEnd = LocalDateTime.of(2026, 3, 8, 3, 0).atZone(chicago).toInstant();
        assertThat(Duration.between(legStart, legEnd).getSeconds())
                .isEqualTo(3 * 60 * 60);

        StudySession session = studySessionService.createSession("DST leg", null, null);
        adjustableClock.at(legStart, () -> studySessionService.startSession(session.getId()));
        adjustableClock.at(legEnd, () -> studySessionService.pauseSession(session.getId()));

        // Park the clock inside the DST week (Sunday local noon, after the
        // leg): the summary then reports the week containing that instant.
        StudyWeeklySummary summary = adjustableClock.parked(
                LocalDateTime.of(2026, 3, 8, 12, 0).atZone(chicago).toInstant(), () ->
                        studySessionService.weeklySummary(chicago));

        assertThat(summary.weekStart()).isEqualTo(
                ZonedDateTime.of(2026, 3, 2, 0, 0, 0, 0, chicago).toInstant());
        long saturday = summary.days().get(5).activeSeconds();
        long sunday = summary.days().get(6).activeSeconds();
        assertThat(saturday).isEqualTo(60 * 60);
        assertThat(sunday).isEqualTo(2 * 60 * 60);
        assertThat(summary.totalSeconds()).isEqualTo(3 * 60 * 60);
        // Every bucket is Monday-based in the requested zone.
        assertThat(summary.days().get(0).date().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void daysEntirelyBeforeCoverageAreZeroAndUncovered() {
        // Invariant over the current week regardless of shared-database
        // contents: a day that ends before interval coverage began has no
        // reliable data — it must be marked uncovered and report zero
        // seconds, never a fabricated number.
        StudyWeeklySummary summary = studySessionService.weeklySummary(ZoneId.of("UTC"));
        Instant coverageStart = summary.coverageStart();

        for (StudyWeeklySummary.DayBucket day : summary.days()) {
            boolean covered = coverageStart != null
                    // Strictly before the day's end: a day whose end equals
                    // the coverage instant has provably zero covered time.
                    && coverageStart.isBefore(
                            day.date().plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant())
                    // Future days of the current week must never report as
                    // covered: data did not exist yet at "now".
                    && !day.date().atStartOfDay(ZoneId.of("UTC")).toInstant()
                            .isAfter(summary.generatedAt());
            assertThat(day.covered()).isEqualTo(covered);
            if (!covered) {
                assertThat(day.activeSeconds()).isEqualTo(0);
            }
        }
        // Bucket consistency: the week total equals the sum of its days.
        assertThat(summary.totalSeconds()).isEqualTo(
                summary.days().stream()
                        .mapToLong(StudyWeeklySummary.DayBucket::activeSeconds).sum());
    }

    private StudySession createStartedAt(Instant when) {
        StudySession session = studySessionService.createSession("Interval run", null, null);
        adjustableClock.at(when, () -> studySessionService.startSession(session.getId()));
        return studySessionService.getSession(session.getId());
    }

    private List<StudyWorkInterval> intervals(UUID sessionId) {
        return intervalRepository.findBySessionId(sessionId);
    }

    private long sessionSeconds(UUID sessionId, Instant windowStart, Instant windowEnd) {
        return studySessionService.sumWorkSecondsOverlapping(sessionId, windowStart, windowEnd);
    }

    private static Instant zoned(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC).toInstant();
    }
}
