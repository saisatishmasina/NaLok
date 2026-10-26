package com.shaktee.nalok.projects.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProjectSectionTest {

    @Test
    void newSectionKeepsProjectTitleAndPosition() {
        Project project = new Project("NaLok", null);

        ProjectSection section = new ProjectSection(
                project, "  Research  ", "notes", 2);

        assertThat(section.getId()).isNotNull();
        assertThat(section.getProject()).isSameAs(project);
        assertThat(section.getTitle()).isEqualTo("Research");
        assertThat(section.getDescription()).isEqualTo("notes");
        assertThat(section.getPosition()).isEqualTo(2);
        assertThat(section.getCreatedAt()).isNotNull();
    }

    @Test
    void requiresProject() {
        assertThatThrownBy(() -> new ProjectSection(null, "Research", null, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void validatesTitle() {
        Project project = new Project("NaLok", null);

        assertThatThrownBy(() -> new ProjectSection(project, " ", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectSection(project, "y".repeat(201), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativePosition() {
        Project project = new Project("NaLok", null);

        assertThatThrownBy(() -> new ProjectSection(project, "Research", null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renameAndReposition() {
        Project project = new Project("NaLok", null);
        ProjectSection section = new ProjectSection(project, "Research", null, 0);

        section.rename("  Renamed  ");
        section.updateDescription("updated");
        section.changePosition(5);

        assertThat(section.getTitle()).isEqualTo("Renamed");
        assertThat(section.getDescription()).isEqualTo("updated");
        assertThat(section.getPosition()).isEqualTo(5);

        assertThatThrownBy(() -> section.changePosition(-3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(section.getPosition()).isEqualTo(5);
    }
}
