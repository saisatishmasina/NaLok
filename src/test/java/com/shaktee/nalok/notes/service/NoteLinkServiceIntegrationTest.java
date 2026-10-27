package com.shaktee.nalok.notes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
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
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;

import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.service.StudySessionService;
import com.shaktee.nalok.notes.model.Note;
import com.shaktee.nalok.notes.model.NoteKind;
import com.shaktee.nalok.notes.model.NoteProjectLink;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

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
    void simultaneousAttachesSerializeAndProduceExactlyOneLink() throws Exception {
        Note note = noteService.createNote("Concurrent attach", "Body", NoteKind.NOTE);
        Project project = projectService.createProject("Attach race", null);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<NoteProjectLink>> results;
        try {
            results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return noteLinkService.linkToProject(note.getId(), project.getId());
                }));
            }
            // Collect while the pool is still alive.
            results.get(0).get(10, TimeUnit.SECONDS);
            results.get(1).get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        // Both requests succeed (the second waits on the guard, finds the
        // existing row and answers idempotently) — no collision error.
        assertThat(noteLinkService.listProjectLinks(note.getId())).hasSize(1);
    }

    @Test
    void linkTableForeignKeysAreEnforcedByTheDatabase() {
        Note note = noteService.createNote("FK check", "Body", NoteKind.NOTE);
        Project project = projectService.createProject("FK project", null);

        // A link row pointing at a note that does not exist must be refused
        // by the database itself, not only by service validation.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO note_project_links (id, note_id, project_id, linked_at) "
                        + "VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(?), "
                        + "UUID_TO_BIN(?), UTC_TIMESTAMP(6))",
                UUID.randomUUID().toString(), project.getId().toString()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(noteLinkService.listProjectLinks(note.getId())).isEmpty();
    }

    @Test
    void linkGuardIsReleasedAfterCommit() throws Exception {
        Note note = noteService.createNote("Guard commit", "Body", NoteKind.NOTE);
        Project project = projectService.createProject("Guard commit target", null);

        noteLinkService.linkToProject(note.getId(), project.getId());

        // Prove release from a DIFFERENT physical connection: MySQL named
        // locks are reentrant on the holding connection, so a same-connection
        // check would be inconclusive.
        try (Connection held = dataSource.getConnection();
             Connection probe = dataSource.getConnection()) {
            assertThat(isUsedLock(probe, "nalok:note-links:" + note.getId()))
                    .as("note-link guard must be released after commit")
                    .isNull();
        }
    }

    @Test
    void linkGuardIsReleasedAfterRollback() throws Exception {
        Note note = noteService.createNote("Guard rollback", "Body", NoteKind.NOTE);

        // Attaching to an unknown project acquires the guard, then fails and
        // rolls back. A leaked guard would make the next attach time out.
        assertThatThrownBy(() -> noteLinkService.linkToProject(note.getId(),
                UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);

        try (Connection held = dataSource.getConnection();
             Connection probe = dataSource.getConnection()) {
            assertThat(isUsedLock(probe, "nalok:note-links:" + note.getId()))
                    .as("note-link guard must be released after rollback")
                    .isNull();
        }

        // And the guard actually closed the window: a later attach succeeds.
        Project project = projectService.createProject("After rollback", null);
        assertThat(noteLinkService.linkToProject(note.getId(), project.getId()))
                .isNotNull();
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

    private List<UUID> pageIds(Page<Note> page) {
        return page.getContent().stream().map(Note::getId).toList();
    }

    @Test
    void contextPagesAreBoundedDedupedAndDisjoint() {
        Note first = noteService.createNote("Ctx 1", "1", NoteKind.NOTE);
        Note second = noteService.createNote("Ctx 2", "2", NoteKind.NOTE);
        Note third = noteService.createNote("Ctx 3", "3", NoteKind.NOTE);
        Project project = projectService.createProject("Paged context", null);
        ProjectSection section = projectSectionService.createSection(
                project.getId(), "Paged section", null, 0);

        noteLinkService.linkToProject(first.getId(), project.getId());
        noteLinkService.linkToProject(second.getId(), project.getId());
        noteLinkService.linkToProject(third.getId(), project.getId());
        noteLinkService.linkToSection(second.getId(), section.getId());

        // Project page 1/1: direct + section links, deduplicated DB-side.
        Page<Note> full = noteLinkService.listNotesForProjectPage(project.getId(), 0, 10);
        assertThat(full.getTotalElements()).isEqualTo(3);
        assertThat(full.getContent()).extracting(Note::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId(), third.getId());

        // Two disjoint pages of size 2 cover all three.
        Page<Note> page0 = noteLinkService.listNotesForProjectPage(project.getId(), 0, 2);
        Page<Note> page1 = noteLinkService.listNotesForProjectPage(project.getId(), 1, 2);
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(pageIds(page0)).doesNotContainAnyElementsOf(pageIds(page1));
        assertThat(full.getTotalElements()).isEqualTo(3);

        // Section page answers only the section-linked note.
        Page<Note> forSection =
                noteLinkService.listNotesForSectionPage(section.getId(), 0, 10);
        assertThat(forSection.getTotalElements()).isEqualTo(1);
        assertThat(forSection.getContent()).extracting(Note::getId)
                .containsExactly(second.getId());
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

        // Rejections happen before any write: nothing was stored anywhere.
        assertThat(noteLinkService.listProjectLinks(note.getId())).isEmpty();
        assertThat(noteLinkService.listSectionLinks(note.getId())).isEmpty();
        assertThat(noteLinkService.listSessionLinks(note.getId())).isEmpty();
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
