package com.shaktee.nalok.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.shaktee.nalok.projects.model.Project;
import com.shaktee.nalok.projects.repository.ProjectRepository;

/**
 * Proves lost-update protection: two detached views of the same project are
 * loaded, both saved — the stale second write must be rejected by @Version,
 * and the first write must win.
 */
@SpringBootTest
class ProjectOptimisticLockingTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void staleProjectUpdateIsRejectedAndFirstWriteWins() {
        Project created = projectService.createProject("OptLock", null);
        UUID id = created.getId();

        Project firstView = projectRepository.findById(id).orElseThrow();
        Project secondView = projectRepository.findById(id).orElseThrow();

        firstView.rename("First write");
        Project savedFirst = projectRepository.saveAndFlush(firstView);

        secondView.rename("Stale write");
        assertThatThrownBy(() -> projectRepository.saveAndFlush(secondView))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        Project stored = projectRepository.findById(id).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("First write");
        assertThat(stored.getVersion())
                .isEqualTo(savedFirst.getVersion())
                .isGreaterThan(0);
    }
}
