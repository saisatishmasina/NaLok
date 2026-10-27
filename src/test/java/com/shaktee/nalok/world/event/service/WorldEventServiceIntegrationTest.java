package com.shaktee.nalok.world.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.service.StudySessionService;
import com.shaktee.nalok.web.NotFoundException;
import com.shaktee.nalok.world.event.WorldEvent;
import com.shaktee.nalok.world.event.WorldEventKind;
import com.shaktee.nalok.world.event.repository.WorldEventRepository;
import com.shaktee.nalok.world.reference.EntityReference;
import com.shaktee.nalok.world.reference.EntityType;

/**
 * E01a behavior: every committed study transition leaves its event, every
 * rejected transition leaves none, and events share the caller's
 * transaction — a rolled-back caller must NOT leave an event behind (that
 * is the atomicity guarantee; a REQUIRES_NEW implementation would fail the
 * rollback test by construction).
 */
@SpringBootTest
class WorldEventServiceIntegrationTest {

    @Autowired
    private StudySessionService studySessionService;

    @Autowired
    private WorldEventService worldEventService;

    @Autowired
    private WorldEventRepository eventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void committedLifecycleLeavesACompleteOrderedTrail() throws Exception {
        StudySession a = studySessionService.createSession("Trail A", null, null);
        Thread.sleep(5);
        StudySession b = studySessionService.createSession("Trail B", null, null);
        Thread.sleep(5);

        studySessionService.startSession(a.getId());
        Thread.sleep(5);
        studySessionService.startSession(b.getId());
        Thread.sleep(5);
        studySessionService.resumeSession(a.getId());
        Thread.sleep(5);
        studySessionService.finishSession(a.getId());

        EntityReference targetA = EntityReference.of(EntityType.STUDY_SESSION, a.getId());
        List<WorldEvent> trailA = worldEventService
                .listEvents(null, targetA, 0, 50).getContent();
        // Newest first; the lifecycle read backwards is the full story.
        assertThat(trailA).extracting(WorldEvent::getKind).containsExactly(
                WorldEventKind.STUDY_SESSION_FINISHED,
                WorldEventKind.STUDY_SESSION_RESUMED,
                WorldEventKind.STUDY_SESSION_AUTO_PAUSED,
                WorldEventKind.STUDY_SESSION_STARTED,
                WorldEventKind.STUDY_SESSION_CREATED);
        assertThat(trailA).allSatisfy(event ->
                assertThat(event.getTarget().getReferenceId()).isEqualTo(a.getId()));

        // Starting B auto-paused A (D06) — the event names A, not B.
        List<WorldEvent> trailB = worldEventService.listEvents(null,
                EntityReference.of(EntityType.STUDY_SESSION, b.getId()), 0, 50)
                .getContent();
        // Resuming A auto-paused B (D06) — so B's trail ends with that fact.
        assertThat(trailB).extracting(WorldEvent::getKind).containsExactly(
                WorldEventKind.STUDY_SESSION_AUTO_PAUSED,
                WorldEventKind.STUDY_SESSION_STARTED,
                WorldEventKind.STUDY_SESSION_CREATED);

        long autoPausedA = worldEventService
                .listEvents(WorldEventKind.STUDY_SESSION_AUTO_PAUSED, null, 0, 50)
                .getContent().stream()
                .filter(event -> event.getTarget().getReferenceId().equals(a.getId()))
                .count();
        assertThat(autoPausedA).isEqualTo(1);
    }

    @Test
    void rejectedTransitionsLeaveNoEvents() {
        StudySession session = studySessionService.createSession("Rejected", null, null);
        studySessionService.startSession(session.getId());
        studySessionService.finishSession(session.getId());
        long eventsAfterFinish = countForTarget(session.getId());

        // A finished session cannot finish again — and must not leave an
        // event describing an operation that never committed.
        assertThatThrownBy(() -> studySessionService.finishSession(session.getId()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> studySessionService
                .recoverInterrupted(session.getId()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> studySessionService
                .startSession(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);

        assertThat(countForTarget(session.getId())).isEqualTo(eventsAfterFinish);
    }

    @Test
    void rolledBackCallerTransactionLeavesNoEvent() {
        // The startup runner records WORLD_STARTED once per fresh database,
        // so only the RELATIVE count can be asserted here.
        long before = eventRepository.count();

        new TransactionTemplate(transactionManager).execute(status -> {
            worldEventService.record(WorldEventKind.WORLD_STARTED,
                    "Ghost event — must never persist", null);
            status.setRollbackOnly();
            return null;
        });

        assertThat(eventRepository.count())
                .as("a rolled-back caller transaction must not leave an event")
                .isEqualTo(before);
    }

    @Test
    void eventPagesAreBoundedAndDisjoint() {
        StudySession session = studySessionService.createSession("Paged trail", null, null);
        EntityReference target = EntityReference.of(EntityType.STUDY_SESSION,
                session.getId());

        Page<WorldEvent> page0 = worldEventService.listEvents(null, target, 0, 1);
        Page<WorldEvent> page1 = worldEventService.listEvents(null, target, 1, 1);
        assertThat(page0.getTotalElements()).isEqualTo(1);
        assertThat(page0.getContent()).hasSize(1);
        assertThat(page1.getContent()).isEmpty();

        Page<WorldEvent> capped = worldEventService.listEvents(null, null, 0, 500);
        assertThat(capped.getSize()).isEqualTo(100);
    }

    private long countForTarget(UUID sessionId) {
        return worldEventService.listEvents(null,
                EntityReference.of(EntityType.STUDY_SESSION, sessionId), 0, 100)
                .getTotalElements();
    }
}
