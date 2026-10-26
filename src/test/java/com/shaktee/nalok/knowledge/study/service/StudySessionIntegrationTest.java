package com.shaktee.nalok.knowledge.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.model.StudySessionStatus;
import com.shaktee.nalok.projects.model.Project;
import com.shaktee.nalok.projects.model.ProjectSection;
import com.shaktee.nalok.projects.service.ProjectSectionService;
import com.shaktee.nalok.projects.service.ProjectService;
import com.shaktee.nalok.world.reference.EntityReference;
import com.shaktee.nalok.world.reference.EntityType;

/**
 * Runs against a disposable MySQL database (src/test/resources/application.properties).
 * Covers S01 reference round trips and S02 lifecycle persistence.
 */
@SpringBootTest
class StudySessionIntegrationTest {

    @Autowired
    private StudySessionService studySessionService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectSectionService sectionService;

    @Autowired
    private DataSource dataSource;

    @Test
    void standaloneSessionRoundTripWithAbsentContext() {
        StudySession created = studySessionService.createSession(
                "Standalone", "no project", null);

        StudySession reloaded = studySessionService.getSession(created.getId());
        assertThat(reloaded.getTitle()).isEqualTo("Standalone");
        assertThat(reloaded.getStatus()).isEqualTo(StudySessionStatus.PLANNED);
        assertThat(reloaded.getContext()).isNull();
        assertThat(reloaded.getActiveWorkTime()).isEqualTo(Duration.ZERO);
    }

    @Test
    void projectContextRoundTrip() {
        Project project = projectService.createProject("Context target", null);
        StudySession created = studySessionService.createSession(
                "For project", null,
                EntityReference.of(EntityType.PROJECT, project.getId()));

        StudySession reloaded = studySessionService.getSession(created.getId());
        assertThat(reloaded.getContext()).isNotNull();
        assertThat(reloaded.getContext().getEntityType()).isEqualTo(EntityType.PROJECT);
        assertThat(reloaded.getContext().getReferenceId()).isEqualTo(project.getId());
    }

    @Test
    void sectionContextRoundTrip() {
        Project project = projectService.createProject("Section host", null);
        ProjectSection section = sectionService.createSection(
                project.getId(), "Chapter 1", null, 0);
        StudySession created = studySessionService.createSession(
                "For section", null,
                EntityReference.of(EntityType.PROJECT_SECTION, section.getId()));

        StudySession reloaded = studySessionService.getSession(created.getId());
        assertThat(reloaded.getContext()).isNotNull();
        assertThat(reloaded.getContext().getEntityType()).isEqualTo(EntityType.PROJECT_SECTION);
        assertThat(reloaded.getContext().getReferenceId()).isEqualTo(section.getId());
    }

    @Test
    void missingContextTargetIsRejected() {
        assertThatThrownBy(() -> studySessionService.createSession(
                "Broken", null, EntityReference.of(EntityType.PROJECT, UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> studySessionService.createSession(
                "Broken", null, EntityReference.of(EntityType.PROJECT_SECTION, UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lifecycleTransitionsPersist() {
        StudySession created = studySessionService.createSession("Lifecycle", null, null);

        studySessionService.startSession(created.getId());
        StudySession running = studySessionService.getSession(created.getId());
        assertThat(running.getStatus()).isEqualTo(StudySessionStatus.ACTIVE);
        assertThat(running.getStartedAt()).isNotNull();
        assertThat(running.getActiveSince()).isNotNull();

        studySessionService.pauseSession(created.getId());
        StudySession paused = studySessionService.getSession(created.getId());
        assertThat(paused.getStatus()).isEqualTo(StudySessionStatus.PAUSED);
        assertThat(paused.getActiveSince()).isNull();

        studySessionService.finishSession(created.getId());
        StudySession finished = studySessionService.getSession(created.getId());
        assertThat(finished.getStatus()).isEqualTo(StudySessionStatus.COMPLETED);
        assertThat(finished.getCompletedAt()).isNotNull();
        assertThat(finished.getActiveWorkTime()).isGreaterThanOrEqualTo(Duration.ZERO);
    }

    @Test
    void startingAnotherSessionAutoPausesTheActiveOne() throws Exception {
        StudySession first = studySessionService.createSession("First", null, null);
        StudySession second = studySessionService.createSession("Second", null, null);

        studySessionService.startSession(first.getId());
        Thread.sleep(50);
        studySessionService.startSession(second.getId());

        StudySession firstAfter = studySessionService.getSession(first.getId());
        assertThat(firstAfter.getStatus()).isEqualTo(StudySessionStatus.PAUSED);
        assertThat(firstAfter.getActiveSince()).isNull();
        assertThat(firstAfter.getActiveWorkTime()).isGreaterThan(Duration.ZERO);

        StudySession secondAfter = studySessionService.getSession(second.getId());
        assertThat(secondAfter.getStatus()).isEqualTo(StudySessionStatus.ACTIVE);

        List<StudySession> active = studySessionService.listSessions(StudySessionStatus.ACTIVE);
        assertThat(active).extracting(StudySession::getId)
                .containsExactly(second.getId());
    }

    @Test
    void resumingAnotherSessionAlsoAutoPauses() throws Exception {
        StudySession first = studySessionService.createSession("A", null, null);
        StudySession second = studySessionService.createSession("B", null, null);

        studySessionService.startSession(first.getId());
        studySessionService.pauseSession(first.getId());
        studySessionService.startSession(second.getId());
        Thread.sleep(50);
        studySessionService.resumeSession(first.getId());

        StudySession secondAfter = studySessionService.getSession(second.getId());
        assertThat(secondAfter.getStatus()).isEqualTo(StudySessionStatus.PAUSED);
        assertThat(studySessionService.getSession(first.getId()).getStatus())
                .isEqualTo(StudySessionStatus.ACTIVE);
    }

    @Test
    void recoveryClosesInterruptedActiveLeg() throws Exception {
        StudySession created = studySessionService.createSession("Interrupted", null, null);
        studySessionService.startSession(created.getId());
        Thread.sleep(50);

        StudySession recovered = studySessionService.recoverInterrupted(created.getId());
        assertThat(recovered.getStatus()).isEqualTo(StudySessionStatus.PAUSED);
        assertThat(recovered.getActiveSince()).isNull();
        assertThat(recovered.getActiveWorkTime()).isGreaterThan(Duration.ZERO);

        assertThatThrownBy(() -> studySessionService.recoverInterrupted(created.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownSessionIsRejected() {
        assertThatThrownBy(() -> studySessionService.getSession(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simultaneousStartsLeaveExactlyOneActiveSession() throws Exception {
        StudySession first = studySessionService.createSession("Concurrent A", null, null);
        StudySession second = studySessionService.createSession("Concurrent B", null, null);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<?> startFirst = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return studySessionService.startSession(first.getId());
            });
            Future<?> startSecond = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return studySessionService.startSession(second.getId());
            });

            startFirst.get(30, TimeUnit.SECONDS);
            startSecond.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        List<StudySession> active = studySessionService.listSessions(StudySessionStatus.ACTIVE);
        assertThat(active).as("exactly one session may be active after concurrent starts")
                .hasSize(1);

        StudySession sessionA = studySessionService.getSession(first.getId());
        StudySession sessionB = studySessionService.getSession(second.getId());
        assertThat(List.of(sessionA.getStatus(), sessionB.getStatus()))
                .containsExactlyInAnyOrder(
                        StudySessionStatus.ACTIVE, StudySessionStatus.PAUSED);

        try (Connection held = dataSource.getConnection();
             Connection probe = dataSource.getConnection()) {
            assertThat(isUsedLock(probe, "nalok:active-session"))
                    .as("guard must be released after commit")
                    .isNull();
        }
    }

    @Test
    void failedStartReleasesTheGuard() throws Exception {
        // startSession on an unknown id acquires the guard, then fails and
        // rolls back. A leaked guard would make the next start time out (10s)
        // and throw, failing this test.
        assertThatThrownBy(() -> studySessionService.startSession(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);

        // Prove release from a different physical connection: if the guard
        // leaked, IS_USED_LOCK reports the holding connection instead of NULL.
        // MySQL named locks are reentrant on the holding connection, so a
        // same-connection check alone would be inconclusive.
        try (Connection held = dataSource.getConnection();
             Connection probe = dataSource.getConnection()) {
            assertThat(isUsedLock(probe, "nalok:active-session"))
                    .as("guard must be released after rollback")
                    .isNull();
        }

        StudySession session = studySessionService.createSession("After failure", null, null);
        studySessionService.startSession(session.getId());

        assertThat(studySessionService.getSession(session.getId()).getStatus())
                .isEqualTo(StudySessionStatus.ACTIVE);
    }

    /**
     * NULL when the named lock is free; otherwise the holder's connection id.
     * Uses a raw statement to keep the probe off any transaction context.
     */
    private static Object isUsedLock(Connection connection, String lockName) throws Exception {
        try (ResultSet result = connection.createStatement()
                .executeQuery("SELECT IS_USED_LOCK('" + lockName + "')")) {
            result.next();
            return result.getObject(1);
        }
    }
}
