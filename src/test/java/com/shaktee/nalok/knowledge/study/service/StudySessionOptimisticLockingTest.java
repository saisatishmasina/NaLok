package com.shaktee.nalok.knowledge.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.repository.StudySessionRepository;

/**
 * Proves lost-update protection on StudySession: a stale detached update must
 * be rejected by @Version while the fresh write wins.
 */
@SpringBootTest
class StudySessionOptimisticLockingTest {

    @Autowired
    private StudySessionService studySessionService;

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Test
    void staleSessionUpdateIsRejectedAndFirstWriteWins() {
        StudySession created = studySessionService.createSession("OptLock", null, null);
        UUID id = created.getId();

        StudySession firstView = studySessionRepository.findById(id).orElseThrow();
        StudySession secondView = studySessionRepository.findById(id).orElseThrow();

        firstView.rename("First write");
        StudySession savedFirst = studySessionRepository.saveAndFlush(firstView);

        secondView.rename("Stale write");
        assertThatThrownBy(() -> studySessionRepository.saveAndFlush(secondView))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        StudySession stored = studySessionRepository.findById(id).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("First write");
        assertThat(stored.getVersion())
                .isEqualTo(savedFirst.getVersion())
                .isGreaterThan(0);
    }
}
