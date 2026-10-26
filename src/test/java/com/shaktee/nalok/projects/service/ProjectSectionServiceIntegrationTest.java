package com.shaktee.nalok.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.shaktee.nalok.projects.model.Project;
import com.shaktee.nalok.projects.model.ProjectSection;

/**
 * Runs against a disposable MySQL database (src/test/resources/application.properties).
 */
@SpringBootTest
class ProjectSectionServiceIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectSectionService sectionService;

    @Test
    void createdSectionIsPersistedWithProjectLink() {
        Project project = projectService.createProject("Sections", null);

        ProjectSection section = sectionService.createSection(
                project.getId(), "Research", "reading list", 0);

        ProjectSection reloaded = sectionService.getSection(section.getId());
        assertThat(reloaded.getTitle()).isEqualTo("Research");
        assertThat(reloaded.getDescription()).isEqualTo("reading list");
        assertThat(reloaded.getPosition()).isEqualTo(0);
        assertThat(reloaded.getProject().getId()).isEqualTo(project.getId());
    }

    @Test
    void listSectionsOrdersByPosition() {
        Project project = projectService.createProject("Ordering", null);

        sectionService.createSection(project.getId(), "Second", null, 1);
        sectionService.createSection(project.getId(), "First", null, 0);
        sectionService.createSection(project.getId(), "Third", null, 2);

        List<ProjectSection> sections = sectionService.listSections(project.getId());
        assertThat(sections).extracting(ProjectSection::getTitle)
                .containsExactly("First", "Second", "Third");
    }

    @Test
    void renameAndRepositionPersist() {
        Project project = projectService.createProject("Edits", null);
        ProjectSection section = sectionService.createSection(
                project.getId(), "Before", null, 0);

        sectionService.renameSection(section.getId(), "After");
        sectionService.changePosition(section.getId(), 7);

        ProjectSection reloaded = sectionService.getSection(section.getId());
        assertThat(reloaded.getTitle()).isEqualTo("After");
        assertThat(reloaded.getPosition()).isEqualTo(7);
    }

    @Test
    void unknownProjectIsRejected() {
        assertThatThrownBy(() -> sectionService.createSection(
                UUID.randomUUID(), "Orphan", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownSectionIsRejected() {
        assertThatThrownBy(() -> sectionService.getSection(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
