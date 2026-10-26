package com.shaktee.nalok.knowledge.study.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.model.StudySessionStatus;
import com.shaktee.nalok.knowledge.study.repository.StudySessionRepository;
import com.shaktee.nalok.projects.service.ProjectSectionService;
import com.shaktee.nalok.projects.service.ProjectService;
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
    private final ProjectService projectService;
    private final ProjectSectionService projectSectionService;
    private final Clock clock;
    private final JdbcTemplate jdbcTemplate;

    public StudySessionService(
            StudySessionRepository studySessionRepository,
            ProjectService projectService,
            ProjectSectionService projectSectionService,
            Clock clock,
            JdbcTemplate jdbcTemplate) {
        this.studySessionRepository = studySessionRepository;
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
        StudySession session = findSession(sessionId);
        session.pause(now());
        return session;
    }

    @Transactional
    public StudySession finishSession(UUID sessionId) {
        StudySession session = findSession(sessionId);
        session.finish(now());
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
        StudySession session = findSession(sessionId);

        if (session.getStatus() != StudySessionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "only active sessions need interruption recovery");
        }

        session.pause(now());
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
        Instant now = now();
        StudySession session = findSession(sessionId);

        for (StudySession active : studySessionRepository.findByStatus(StudySessionStatus.ACTIVE)) {
            if (!active.getId().equals(sessionId)) {
                active.pause(now);
            }
        }

        if (isResume) {
            session.resume(now);
        } else {
            session.start(now);
        }

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
            throw new IllegalStateException(
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

    private void validateContext(EntityReference context) {
        if (context == null) {
            return;
        }

        boolean exists = switch (context.getEntityType()) {
            case PROJECT -> projectService.projectExists(context.getReferenceId());
            case PROJECT_SECTION -> projectSectionService.sectionExists(context.getReferenceId());
        };

        if (!exists) {
            throw new IllegalArgumentException(
                    "Study context target does not exist: " + context);
        }
    }

    private StudySession findSession(UUID sessionId) {
        return studySessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Study session not found: " + sessionId));
    }

    private Instant now() {
        return clock.instant();
    }
}
