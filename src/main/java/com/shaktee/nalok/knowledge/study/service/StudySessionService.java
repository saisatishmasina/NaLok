package com.shaktee.nalok.knowledge.study.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.model.StudySessionStatus;
import com.shaktee.nalok.knowledge.study.model.StudyReportingCoverage;
import com.shaktee.nalok.knowledge.study.model.StudyWorkInterval;
import com.shaktee.nalok.knowledge.study.repository.StudySessionRepository;
import com.shaktee.nalok.knowledge.study.repository.StudyReportingCoverageRepository;
import com.shaktee.nalok.knowledge.study.repository.StudyWorkIntervalRepository;
import com.shaktee.nalok.projects.service.ProjectSectionService;
import com.shaktee.nalok.projects.service.ProjectService;
import com.shaktee.nalok.web.NotFoundException;
import com.shaktee.nalok.world.reference.EntityReference;
import com.shaktee.nalok.world.reference.EntityType;

@Service
@Transactional(readOnly = true)
public class StudySessionService {

    /**
     * Serializes start/resume across all sessions so the one-focused-session
     * rule (D06) holds even for concurrent requests. MySQL named lock; valid
     * for the single-instance desktop app. Released on the same connection.
     */
    private static final String ACTIVE_SESSION_GUARD = "nalok:active-session";
    private static final int GUARD_TIMEOUT_SECONDS = 10;
    private static final int MAX_HISTORY_PAGE_SIZE = 100;

    private final StudySessionRepository studySessionRepository;
    private final StudyWorkIntervalRepository workIntervalRepository;
    private final StudyReportingCoverageRepository coverageRepository;
    private final ProjectService projectService;
    private final ProjectSectionService projectSectionService;
    private final Clock clock;
    private final JdbcTemplate jdbcTemplate;

    public StudySessionService(
            StudySessionRepository studySessionRepository,
            StudyWorkIntervalRepository workIntervalRepository,
            StudyReportingCoverageRepository coverageRepository,
            ProjectService projectService,
            ProjectSectionService projectSectionService,
            Clock clock,
            JdbcTemplate jdbcTemplate) {
        this.studySessionRepository = studySessionRepository;
        this.workIntervalRepository = workIntervalRepository;
        this.coverageRepository = coverageRepository;
        this.projectService = projectService;
        this.projectSectionService = projectSectionService;
        this.clock = clock;
        this.jdbcTemplate = jdbcTemplate;
    }

    public StudySession getSession(UUID sessionId) {
        return findSession(sessionId);
    }

    public List<StudySession> listSessions(StudySessionStatus status) {
        return studySessionRepository.findByStatus(status);
    }

    /**
     * Bounded, newest-first history for UI lists. Null status = all sessions.
     * Page size is capped so a careless client cannot request an unbounded list.
     */
    public Page<StudySession> listSessionHistory(StudySessionStatus status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_HISTORY_PAGE_SIZE);

        PageRequest pageRequest = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));

        if (status == null) {
            return studySessionRepository.findAll(pageRequest);
        }

        return studySessionRepository.findByStatus(status, pageRequest);
    }

    @Transactional
    public StudySession createSession(String title, String summary, EntityReference context) {
        validateContext(context);
        return studySessionRepository.save(new StudySession(title, summary, context));
    }

    @Transactional
    public StudySession renameSession(UUID sessionId, String title) {
        StudySession session = findSession(sessionId);
        session.rename(title);
        return session;
    }

    @Transactional
    public StudySession updateSummary(UUID sessionId, String summary) {
        StudySession session = findSession(sessionId);
        session.updateSummary(summary);
        return session;
    }

    @Transactional
    public StudySession startSession(UUID sessionId) {
        return startOrResume(sessionId, false);
    }

    @Transactional
    public StudySession resumeSession(UUID sessionId) {
        return startOrResume(sessionId, true);
    }

    @Transactional
    public StudySession pauseSession(UUID sessionId) {
        Instant now = now();
        StudySession session = findSession(sessionId);
        session.pause(now);
        closeAnyOpenInterval(sessionId, now);
        return session;
    }

    @Transactional
    public StudySession finishSession(UUID sessionId) {
        Instant now = now();
        StudySession session = findSession(sessionId);
        StudySessionStatus statusBeforeFinish = session.getStatus();
        session.finish(now);
        if (statusBeforeFinish == StudySessionStatus.ACTIVE) {
            // A paused-session finish was already closed at pause time.
            closeAnyOpenInterval(sessionId, now);
        }
        return session;
    }

    /**
     * Recovery for a session left ACTIVE by an interrupted app run. Closes the
     * open active leg at the current time and pauses the session. Documented
     * limitation: time until recovery is counted as active work; manual
     * duration correction is a separate open decision.
     */
    @Transactional
    public StudySession recoverInterrupted(UUID sessionId) {
        Instant now = now();
        StudySession session = findSession(sessionId);

        if (session.getStatus() != StudySessionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "only active sessions need interruption recovery");
        }

        session.pause(now);
        closeAnyOpenInterval(sessionId, now);
        return session;
    }

    /**
     * One focused session at a time: starting or resuming a session pauses any
     * other ACTIVE session (auto-pause decision, D06). The guard lock makes
     * concurrent starts/resumes serialize; time is captured after acquiring
     * it, so lock waiting is never counted as study time.
     */
    private StudySession startOrResume(UUID sessionId, boolean isResume) {
        acquireActiveSessionGuard();
        releaseGuardAfterTransactionEnds();

        // Rollout bridge detection must happen before the marker is created
        // by this transition's own openInterval (below).
        boolean markerWasAbsent = coverageRepository
                .findById(StudyReportingCoverage.SINGLETON_ID)
                .isEmpty();

        Instant now = now();
        StudySession session = findSession(sessionId);

        for (StudySession active : studySessionRepository.findByStatus(StudySessionStatus.ACTIVE)) {
            if (!active.getId().equals(sessionId)) {
                // Auto-paused session (D06): its open work leg ends now too.
                Instant untrackedLegStart = active.getActiveSince();
                active.pause(now);
                closeAnyOpenInterval(active.getId(), now);

                if (markerWasAbsent && untrackedLegStart != null) {
                    // Rollout bridge (D09): a session ACTIVE before interval
                    // tracking has a REAL ongoing leg whose start instant is
                    // persisted state — record [legStart, now] so its final
                    // leg is accounted for instead of silently lost.
                    StudyWorkInterval bridge = new StudyWorkInterval(active.getId(), untrackedLegStart);
                    bridge.close(now);
                    workIntervalRepository.save(bridge);
                }
            }
        }

        if (isResume) {
            session.resume(now);
        } else {
            session.start(now);
        }
        openInterval(sessionId, now);

        return session;
    }

    /**
     * Releases the guard after commit or rollback, while the transaction's
     * connection is still bound — releasing earlier (before commit) would
     * reopen the concurrency window the guard exists to close.
     */
    private void releaseGuardAfterTransactionEnds() {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        releaseActiveSessionGuard();
                    }
                });
    }

    private void acquireActiveSessionGuard() {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(?, ?)", Integer.class,
                ACTIVE_SESSION_GUARD, GUARD_TIMEOUT_SECONDS);

        if (result == null || result != 1) {
            throw new GuardUnavailableException(
                    "could not acquire the active-session guard; try again");
        }
    }

    private void releaseActiveSessionGuard() {
        try {
            jdbcTemplate.queryForObject(
                    "SELECT RELEASE_LOCK(?)", Integer.class, ACTIVE_SESSION_GUARD);
        } catch (RuntimeException releasedElsewhereOrGone) {
            // Guard release is best effort; the lock dies with the connection.
        }
    }

    /**
     * Opens a work interval for the session's new ACTIVE leg. Must be called
     * in the same transaction as the session transition. Timestamps match the
     * session's own activeSince because both use the same captured instant.
     */
    private void openInterval(UUID sessionId, Instant startedAt) {
        ensureCoverageMarker(startedAt);
        workIntervalRepository.save(new StudyWorkInterval(sessionId, startedAt));
    }

    /**
     * Persists the roll-out instant the first time an interval is recorded,
     * in the same transaction as that transition. This is the stable coverage
     * floor: coverage start resolves to the earliest of this marker and the
     * earliest persisted interval, so leg data recorded before the marker
     * (including rollout-bridge intervals) still counts.
     */
    private void ensureCoverageMarker(Instant rolloutInstant) {
        try {
            if (coverageRepository.findById(StudyReportingCoverage.SINGLETON_ID).isEmpty()) {
                coverageRepository.save(new StudyReportingCoverage(rolloutInstant));
            }
        } catch (DataAccessException concurrentMarkerCreation) {
            // Marker creation is best effort: the fallback read (earliest
            // interval) still yields correct coverage below.
        }
    }

    /**
     * Closes every still-open interval of the session at the given instant —
     * normally one (one active leg at a time). Must be called in the same
     * transaction as the session transition.
     */
    private void closeAnyOpenInterval(UUID sessionId, Instant endedAt) {
        for (StudyWorkInterval open : workIntervalRepository
                .findBySessionIdAndEndedAtIsNull(sessionId)) {
            open.close(endedAt);
        }
    }

    /**
     * Sum of active work of ONE session inside [windowStart, windowEnd), in
     * seconds.
     */
    public long sumWorkSecondsOverlapping(UUID sessionId, Instant windowStart, Instant windowEnd) {
        return sumSpans(workIntervalRepository.findOverlappingSpansBySession(
                sessionId, windowStart, windowEnd, minInstant(now(), windowEnd)),
                windowStart, windowEnd);
    }

    /**
     * Sum of active work of ALL sessions inside [windowStart, windowEnd), in
     * seconds. Closed intervals count their overlap with the window; the open
     * interval of a currently ACTIVE session counts up to min(now, windowEnd)
     * — the repository query coalesces an open ended_at to that reportingNow.
     * Pauses are inherently excluded because only active legs are persisted
     * as intervals.
     */
    public long sumWorkSecondsOverlapping(Instant windowStart, Instant windowEnd) {
        return sumSpans(workIntervalRepository.findOverlappingSpans(
                windowStart, windowEnd, minInstant(now(), windowEnd)),
                windowStart, windowEnd);
    }

    private long sumSpans(
            List<StudyWorkIntervalRepository.IntervalSpanView> spans,
            Instant windowStart, Instant windowEnd) {
        if (!windowEnd.isAfter(windowStart)) {
            return 0;
        }

        long totalSeconds = 0;
        for (StudyWorkIntervalRepository.IntervalSpanView span : spans) {
            totalSeconds += overlapSeconds(
                    span.getStartedAt(), span.getEndedAt(), windowStart, windowEnd);
        }

        return totalSeconds;
    }

    /**
     * Seconds one span contributes to [windowStart, windowEnd). Open legs are
     * already capped by the reportingNow the repo query coalesced, so both
     * endpoints are plain values here.
     */
    private static long overlapSeconds(
            Instant spanStart, Instant spanEnd, Instant windowStart, Instant windowEnd) {
        Instant overlapStart = maxInstant(spanStart, windowStart);
        Instant overlapEnd = minInstant(spanEnd, windowEnd);

        if (!overlapEnd.isAfter(overlapStart)) {
            return 0;
        }
        return Duration.between(overlapStart, overlapEnd).getSeconds();
    }

    /**
     * Weekly focus-time summary for a time zone: Monday-based week, one
     * bucket per day, all derived from persisted intervals. Time-zone and DST
     * boundaries are handled here by converting local midnights, so interval
     * arithmetic itself stays in plain UTC instants.
     */
    public StudyWeeklySummary weeklySummary(ZoneId zone) {
        Instant now = now();
        LocalDate weekStartDate = now.atZone(zone).toLocalDate().with(DayOfWeek.MONDAY);
        Instant weekStart = weekStartDate.atStartOfDay(zone).toInstant();
        Instant weekEnd = weekStartDate.plusDays(7).atStartOfDay(zone).toInstant();
        Instant coverageStart = workIntervalCoverageStart();

        List<StudyWorkIntervalRepository.IntervalSpanView> spans =
                workIntervalRepository.findOverlappingSpans(weekStart, weekEnd, now);

        List<StudyWeeklySummary.DayBucket> days = new ArrayList<>(7);
        long totalSeconds = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate dayDate = weekStartDate.plusDays(i);
            Instant dayStart = dayDate.atStartOfDay(zone).toInstant();
            Instant dayEnd = dayDate.plusDays(1).atStartOfDay(zone).toInstant();

            long daySeconds = 0;
            for (StudyWorkIntervalRepository.IntervalSpanView span : spans) {
                daySeconds += overlapSeconds(
                        span.getStartedAt(), span.getEndedAt(), dayStart, dayEnd);
            }

            days.add(new StudyWeeklySummary.DayBucket(
                    dayDate,
                    dayCovered(coverageStart, dayStart, dayEnd, now),
                    daySeconds));
            totalSeconds += daySeconds;
        }

        return new StudyWeeklySummary(
                zone.getId(), weekStart, weekEnd, now, coverageStart, totalSeconds, List.copyOf(days));
    }

    /**
     * A day is reportable only when a covered portion of it existed by
     * "now": interval coverage must have begun strictly before that day
     * ended, AND the day must already have begun. A day whose end equals
     * the coverage instant has provably zero covered time and stays
     * uncovered — reporting it as covered would fabricate a 0:00 day.
     */
    private static boolean dayCovered(
            Instant coverageStart, Instant dayStart, Instant dayEnd, Instant now) {
        if (coverageStart == null || dayStart.isAfter(now)) {
            return false;
        }
        return coverageStart.isBefore(dayEnd);
    }

    /**
     * When interval-based period reporting became available: the earliest of
     * the persisted roll-out marker and the earliest persisted interval.
     * Intervals recorded before the marker are real, reliably reportable
     * data, so they pull coverage back. Intervals deleted afterwards cannot
     * pull coverage earlier than the marker, which stays as the stable floor.
     * Null when neither exists. Sessions created before roll-out have no
     * reliable per-period breakdown; the UI must not fabricate history.
     */
    public Instant workIntervalCoverageStart() {
        Instant marker = coverageRepository
                .findById(StudyReportingCoverage.SINGLETON_ID)
                .map(StudyReportingCoverage::getCoverageStart)
                .orElse(null);

        List<Instant> earliest = workIntervalRepository.findEarliestIntervalStart(PageRequest.of(0, 1));
        Instant earliestInterval = earliest.isEmpty() ? null : earliest.get(0);

        if (marker == null) {
            return earliestInterval;
        }
        if (earliestInterval == null) {
            return marker;
        }
        return marker.isBefore(earliestInterval) ? marker : earliestInterval;
    }

    private static Instant maxInstant(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant minInstant(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private void validateContext(EntityReference context) {
        if (context == null) {
            return;
        }

        boolean exists = switch (context.getEntityType()) {
            case PROJECT -> projectService.projectExists(context.getReferenceId());
            case PROJECT_SECTION -> projectSectionService.sectionExists(context.getReferenceId());
        };

        if (!exists) {
            throw new ContextTargetNotFoundException(
                    "Study context target does not exist: " + context);
        }
    }

    private StudySession findSession(UUID sessionId) {
        return studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException(
                        "Study session not found: " + sessionId));
    }

    private Instant now() {
        return clock.instant();
    }
}
