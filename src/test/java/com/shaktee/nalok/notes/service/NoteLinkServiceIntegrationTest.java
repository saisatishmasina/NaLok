package com.shaktee.nalok.notes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.service.StudySessionService;
import com.shaktee.nalok.notes.model.Note;
import com.shaktee.nalok.notes.model.NoteKind;
import com.shaktee.nalok.notes.repository.NoteRevisionRepository;
import com.shaktee.nalok.projects.model.Project;
import com.shaktee.nalok.projects.model.ProjectSection;
import com.shaktee.nalok.projects.service.ProjectSectionService;
import com.shaktee.nalok.projects.service.ProjectService;
import com.shaktee.nalok.web.NotFoundException;

/**
 * Association behavior: one note reused across many contexts, attach and
 * unlink never touching content or revisions, duplicate attaches
 * idempotent, unknown targets rejected, and section-linked notes reachable
 * project-wide without a duplicate project link.
 */
@SpringBootTest
class NoteLinkServiceIntegrationTest {

    @Autowired
    private NoteService noteService;

    @Autowired
    private NoteLinkService noteLinkService;

    @Autowired
    private NoteRevisionRepository noteRevisionRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectSectionService projectSectionService;

    @Autowired
    private StudySessionService studySessionService;

    @Test
    void noteIsReusedAcrossTwoProjectsWithoutCopyingContent() {
        Note note = noteService.createNote("Integration method", "Shared explanation",
                NoteKind.NOTE);
        Project first = projectService.createProject("First project", null);
        Project second = projectService.createProject("Second project", null);

        noteLinkService.linkToProject(note.getId(), first.getId());
        noteLinkService.linkToProject(note.getId(), second.getId());

        assertThat(noteLinkService.listNotesForProject(first.getId()))
                .extracting(Note::getId).containsExactly(note.getId());
        assertThat(noteLinkService.listNotesForProject(second.getId()))
                .extracting(Note::getId).containsExactly(note.getId());

        // The note still belongs to itself: one body, one revision trail.
        assertThat(noteService.getNote(note.getId()).getBody())
                .isEqualTo("Shared explanation");
        assertThat(noteRevisionRepository.countByNote_Id(note.getId())).isEqualTo(1);
    }

    @Test
    void duplicateAttachIsIdempotentAndKeepsOriginalLinkedAt() {
        Note note = noteService.createNote("Idempotent", "Body", NoteKind.NOTE);
        Project project = projectService.createProject("Attach twice", null);

        var first = noteLinkService.linkToProject(note.getId(), project.getId());
        var second = noteLinkService.linkToProject(note.getId(), project.getId());

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getLinkedAt()).isEqualTo(first.getLinkedAt());
        assertThat(noteLinkService.listProjectLinks(note.getId())).hasSize(1);
    }

    @Test
    void unknownTargetsAreRejected() {
        Note note = noteService.createNote("Rejected links", "Body", NoteKind.NOTE);

        assertThatThrownBy(() -> noteLinkService.linkToProject(note.getId(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> noteLinkService.linkToSection(note.getId(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> noteLinkService.linkToSession(note.getId(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> noteLinkService.linkToProject(UUID.randomUUID(),
                projectService.createProject("Real project", null).getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unlinkRemovesOnlyTheLink() {
        Note note = noteService.createNote("Unlink me", "Body stays", NoteKind.NOTE);
        Project project = projectService.createProject("Unlink source", null);
        StudySession session = studySessionService.createSession("Session", null, null);

        noteLinkService.linkToProject(note.getId(), project.getId());
        noteLinkService.linkToSession(note.getId(), session.getId());
        noteLinkService.unlinkFromProject(note.getId(), project.getId());

        assertThat(noteLinkService.listProjectLinks(note.getId())).isEmpty();
        assertThat(noteLinkService.listSessionLinks(note.getId())).hasSize(1);
        // Unlinking is content-neutral and revision-neutral.
        Note reloaded = noteService.getNote(note.getId());
        assertThat(reloaded.getBody()).isEqualTo("Body stays");
        assertThat(reloaded.getLatestRevisionNumber()).isEqualTo(1);
        assertThat(noteRevisionRepository.countByNote_Id(note.getId())).isEqualTo(1);

        // Unlinking a never-linked target is a predictable no-op.
        noteLinkService.unlinkFromProject(note.getId(), UUID.randomUUID());
        assertThat(noteService.getNote(note.getId())).isNotNull();
    }

    @Test
    void sectionLinkedNotesAreReachableProjectWide() {
        Note directNote = noteService.createNote("Direct", "On the project", NoteKind.NOTE);
        Note sectionNote = noteService.createNote("Section scoped", "In one section",
                NoteKind.NOTE);
        Project project = projectService.createProject("Wide net", null);
        ProjectSection section = projectSectionService.createSection(
                project.getId(), "Chapter 1", null, 0);

        noteLinkService.linkToProject(directNote.getId(), project.getId());
        noteLinkService.linkToSection(sectionNote.getId(), section.getId());

        var forProject = noteLinkService.listNotesForProject(project.getId());
        assertThat(forProject).extracting(Note::getId)
                .containsExactlyInAnyOrder(directNote.getId(), sectionNote.getId());

        var forSection = noteLinkService.listNotesForSection(section.getId());
        assertThat(forSection).extracting(Note::getId)
                .containsExactly(sectionNote.getId());
    }

    @Test
    void noteLinkedBothWaysAppearsOnceProjectWide() {
        Note note = noteService.createNote("Both ways", "Body", NoteKind.NOTE);
        Project project = projectService.createProject("Dedup source", null);
        ProjectSection section = projectSectionService.createSection(
                project.getId(), "Chapter 2", null, 0);

        noteLinkService.linkToProject(note.getId(), project.getId());
        noteLinkService.linkToSection(note.getId(), section.getId());

        assertThat(noteLinkService.listNotesForProject(project.getId()))
                .extracting(Note::getId)
                .containsExactly(note.getId());
    }

    @Test
    void associatingWithAProjectSectionValidatesItsProject() {
        Note note = noteService.createNote("Validated", "Body", NoteKind.NOTE);
        Project project = projectService.createProject("Section host", null);
        ProjectSection section = projectSectionService.createSection(
                project.getId(), "Chapter 3", null, 0);

        noteLinkService.linkToSection(note.getId(), section.getId());
        assertThat(noteLinkService.listSectionLinks(note.getId())).hasSize(1);
    }

    @Test
    void sessionLinksListTheirNotesNewestFirst() {
        StudySession session = studySessionService.createSession("Linked session", null, null);
        Note old = noteService.createNote("Old note", "1", NoteKind.NOTE);
        Note fresh = noteService.createNote("Fresh note", "2", NoteKind.NOTE);
        noteLinkService.linkToSession(old.getId(), session.getId());
        noteLinkService.linkToSession(fresh.getId(), session.getId());

        var notes = noteLinkService.listNotesForSession(session.getId());
        assertThat(notes).extracting(Note::getId)
                .containsExactly(fresh.getId(), old.getId());
    }
}
