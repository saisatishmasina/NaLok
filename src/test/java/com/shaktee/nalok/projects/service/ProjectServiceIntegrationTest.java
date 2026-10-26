package com.shaktee.nalok.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.shaktee.nalok.projects.model.Project;
import com.shaktee.nalok.projects.model.ProjectStatus;

/**
 * Runs against a disposable MySQL database (src/test/resources/application.properties).
 * Service calls each open their own transaction, so re-reads prove real persistence.
 */
@SpringBootTest
class ProjectServiceIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Test
    void createdProjectIsPersisted() {
        Project created = projectService.createProject("NaLok", "personal world");

        Project reloaded = projectService.getProject(created.getId());
        assertThat(reloaded.getTitle()).isEqualTo("NaLok");
        assertThat(reloaded.getDescription()).isEqualTo("personal world");
        assertThat(reloaded.getStatus()).isEqualTo(ProjectStatus.PLANNED);
    }

    @Test
    void renameAndDescriptionPersist() {
        Project created = projectService.createProject("Temporary", "old");

        projectService.renameProject(created.getId(), "  Renamed  ");
        projectService.updateDescription(created.getId(), "new");

        Project reloaded = projectService.getProject(created.getId());
        assertThat(reloaded.getTitle()).isEqualTo("Renamed");
        assertThat(reloaded.getDescription()).isEqualTo("new");
    }

    @Test
    void lifecycleTransitionsPersist() {
        Project created = projectService.createProject("Lifecycle", null);
        projectService.startProject(created.getId());
        assertThat(projectService.getProject(created.getId()).getStatus())
                .isEqualTo(ProjectStatus.ACIVE);

        projectService.pauseProject(created.getId());
        assertThat(projectService.getProject(created.getId()).getStatus())
                .isEqualTo(ProjectStatus.PAUSED);

        projectService.startProject(created.getId());
        projectService.completeProject(created.getId());
        Project finished = projectService.getProject(created.getId());
        assertThat(finished.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(finished.getCompletedAt()).isNotNull();
    }

    @Test
    void completedProjectAppearsInStatusFilter() {
        Project created = projectService.createProject("FilterMe", null);
        projectService.startProject(created.getId());
        projectService.completeProject(created.getId());

        List<Project> completed = projectService.listProjects(ProjectStatus.COMPLETED);
        assertThat(completed).extracting(Project::getId).contains(created.getId());
    }

    @Test
    void invalidTransitionFailsAndLeavesStoredState() {
        Project created = projectService.createProject("Untouched", null);

        assertThatThrownBy(() -> projectService.completeProject(created.getId()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(projectService.getProject(created.getId()).getStatus())
                .isEqualTo(ProjectStatus.PLANNED);
    }

    @Test
    void unknownProjectIsRejected() {
        assertThatThrownBy(() -> projectService.getProject(
                java.util.UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
