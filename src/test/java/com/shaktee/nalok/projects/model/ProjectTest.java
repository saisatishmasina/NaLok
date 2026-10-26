package com.shaktee.nalok.projects.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ProjectTest {

    @Test
    void newProjectStartsPlannedWithTimestamp() {
        Project project = new Project("NaLok", "personal world");

        assertThat(project.getId()).isNotNull();
        assertThat(project.getTitle()).isEqualTo("NaLok");
        assertThat(project.getDescription()).isEqualTo("personal world");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.PLANNED);
        assertThat(project.getCreatedAt()).isNotNull();
        assertThat(project.getCompletedAt()).isNull();
    }

    @Test
    void titleIsStripped() {
        Project project = new Project("  NaLok  ", null);

        assertThat(project.getTitle()).isEqualTo("NaLok");
    }

    @Test
    void rejectsEmptyTitle() {
        assertThatThrownBy(() -> new Project(null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Project("   ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTooLongTitle() {
        assertThatThrownBy(() -> new Project("x".repeat(201), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renameValidatesTitle() {
        Project project = new Project("NaLok", null);

        project.rename("  Renamed  ");
        assertThat(project.getTitle()).isEqualTo("Renamed");

        assertThatThrownBy(() -> project.rename(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startFromPlannedActivates() {
        Project project = new Project("NaLok", null);

        project.start();

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACIVE);
    }

    @Test
    void startFromPausedActivates() {
        Project project = new Project("NaLok", null);
        project.start();
        project.pause();

        project.start();

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACIVE);
    }

    @Test
    void startFromCompletedIsRejected() {
        Project project = new Project("NaLok", null);
        project.start();
        project.complete();

        assertThatThrownBy(project::start)
                .isInstanceOf(IllegalStateException.class);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
    }

    @Test
    void startFromArchivedIsRejected() {
        Project project = new Project("NaLok", null);
        project.archive();

        assertThatThrownBy(project::start)
                .isInstanceOf(IllegalStateException.class);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
    }

    @Test
    void pauseOnlyFromActive() {
        Project planned = new Project("NaLok", null);
        assertThatThrownBy(planned::pause)
                .isInstanceOf(IllegalStateException.class);

        planned.start();
        planned.pause();
        assertThat(planned.getStatus()).isEqualTo(ProjectStatus.PAUSED);
    }

    @Test
    void completeOnlyFromActiveAndRecordsTimestamp() {
        Project planned = new Project("NaLok", null);
        assertThatThrownBy(planned::complete)
                .isInstanceOf(IllegalStateException.class);
        assertThat(planned.getCompletedAt()).isNull();

        planned.start();
        planned.complete();
        assertThat(planned.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(planned.getCompletedAt()).isInstanceOf(Instant.class);
    }

    @Test
    void pauseThenCompleteIsRejectedWithoutTimestamp() {
        Project project = new Project("NaLok", null);
        project.start();
        project.pause();

        assertThatThrownBy(project::complete)
                .isInstanceOf(IllegalStateException.class);
        assertThat(project.getCompletedAt()).isNull();
    }

    @Test
    void archiveAllowedFromAnyLiveStateButNotTwice() {
        Project planned = new Project("NaLok", null);
        planned.archive();
        assertThat(planned.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);

        assertThatThrownBy(planned::archive)
                .isInstanceOf(IllegalStateException.class);
    }
}
