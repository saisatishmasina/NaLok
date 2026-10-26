package com.shaktee.nalok.knowledge.study.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final StudySessionRepository studySessionRepository;
    private final ProjectService projectService;
    private final ProjectSectionService projectSectionService;
    private final Clock clock;

    public StudySessionService(
            StudySessionRepository studySessionRepository,
            ProjectService projectService,
            ProjectSectionService projectSectionService,
            Clock clock) {
        this.studySessionRepository = studySessionRepository;
        this.projectService = projectService;
        this.projectSectionService = projectSectionService;
        this.clock = clock;
    }

    public StudySession getSession(UUID sessionId) {
        return findSession(sessionId);
    }

    public List<StudySession> listSessions(StudySessionStatus status) {
        return studySessionRepository.findByStatus(status);
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
     * other ACTIVE session (auto-pause decision, D07).
     */
    private StudySession startOrResume(UUID sessionId, boolean isResume) {
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
