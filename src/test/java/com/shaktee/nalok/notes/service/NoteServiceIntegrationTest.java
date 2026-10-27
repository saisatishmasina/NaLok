package com.shaktee.nalok.notes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.shaktee.nalok.notes.model.Note;
import com.shaktee.nalok.notes.model.NoteKind;
import com.shaktee.nalok.notes.model.NoteRevision;
import com.shaktee.nalok.notes.model.NoteRevisionSummary;
import com.shaktee.nalok.notes.repository.NoteRepository;
import com.shaktee.nalok.notes.repository.NoteRevisionRepository;
import com.shaktee.nalok.web.NotFoundException;

/**
 * Persistence-level behavior for independent notes: safe writing survives
 * reload (including Unicode and multiline content), edits preserve every
 * earlier revision, conflicting edits cannot overwrite newer work, and a
 * failed save leaves neither a partial update nor an orphan revision.
 */
@SpringBootTest
class NoteServiceIntegrationTest {

    @Autowired
    private NoteService noteService;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private NoteRevisionRepository noteRevisionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void unicodeAndMultilineBodySurviveReload() {
        String body = "Intégration 🔍 方法\n\n```java\nassertThat(x).isTrue();\n```\n\ttabbed tail";

        Note created = noteService.createNote("Unicode round trip", body, NoteKind.NOTE);

        Note reloaded = noteService.getNote(created.getId());
        assertThat(reloaded.getTitle()).isEqualTo("Unicode round trip");
        assertThat(reloaded.getBody()).isEqualTo(body);
        assertThat(reloaded.getKind()).isEqualTo(NoteKind.NOTE);
        assertThat(reloaded.isArchived()).isFalse();
        assertThat(reloaded.getCreatedAt()).isEqualTo(reloaded.getUpdatedAt());
    }

    @Test
    void creationRecordsTheInitialRevision() {
        Note created = noteService.createNote("Initial rev", "Body v1", NoteKind.THOUGHT);

        List<NoteRevision> revisions = noteService.listRevisions(created.getId());
        assertThat(revisions).hasSize(1);

        NoteRevision initial = revisions.get(0);
        assertThat(initial.getRevisionNumber()).isEqualTo(1);
        assertThat(initial.getTitle()).isEqualTo("Initial rev");
        assertThat(initial.getBody()).isEqualTo("Body v1");
        assertThat(initial.getKind()).isEqualTo(NoteKind.THOUGHT);
        assertThat(initial.getRecordedAt()).isEqualTo(created.getCreatedAt());
        assertThat(noteRevisionRepository.countByNote_Id(created.getId())).isEqualTo(1);
    }

    @Test
    void contentChangingSaveAddsRevisionAndOlderWritingIsPreserved() {
        Note created = noteService.createNote("Evolution", "Body v1", NoteKind.NOTE);
        Note note = noteService.getNote(created.getId());

        noteService.updateNote(created.getId(), "Evolution", "Body v2 — much better", null,
                note.getVersion());

        Note reloaded = noteService.getNote(created.getId());
        assertThat(reloaded.getBody()).isEqualTo("Body v2 — much better");
        assertThat(reloaded.getVersion()).isEqualTo(note.getVersion() + 1);
        assertThat(reloaded.getLatestRevisionNumber()).isEqualTo(2);

        List<NoteRevision> revisions = noteService.listRevisions(created.getId());
        assertThat(revisions).hasSize(2);
        // Newest first: revision 2 on top, revision 1 untouched below.
        assertThat(revisions.get(0).getRevisionNumber()).isEqualTo(2);
        assertThat(revisions.get(0).getBody()).isEqualTo("Body v2 — much better");
        assertThat(revisions.get(1).getRevisionNumber()).isEqualTo(1);
        assertThat(revisions.get(1).getBody()).isEqualTo("Body v1");
    }

    @Test
    void unchangedSaveCreatesNoRevision() {
        Note created = noteService.createNote("Stable", "Same body", NoteKind.NOTE);
        Note note = noteService.getNote(created.getId());

        noteService.updateNote(created.getId(), "Stable", "Same body", NoteKind.NOTE,
                note.getVersion());

        Note reloaded = noteService.getNote(created.getId());
        assertThat(reloaded.getLatestRevisionNumber()).isEqualTo(1);
        assertThat(noteRevisionRepository.countByNote_Id(created.getId())).isEqualTo(1);
        // No writing change, no record change: updatedAt stays at creation.
        assertThat(reloaded.getUpdatedAt()).isEqualTo(reloaded.getCreatedAt());
        assertThat(reloaded.getVersion()).isEqualTo(note.getVersion());
    }

    @Test
    void kindOnlyChangeRecordsRevision() {
        Note created = noteService.createNote("Promoted", "Reasoning…", NoteKind.THOUGHT);
        Note note = noteService.getNote(created.getId());

        noteService.updateNote(created.getId(), "Promoted", "Reasoning…", NoteKind.DECISION,
                note.getVersion());

        List<NoteRevision> revisions = noteService.listRevisions(created.getId());
        assertThat(revisions).hasSize(2);
        assertThat(revisions.get(0).getKind()).isEqualTo(NoteKind.DECISION);
        assertThat(revisions.get(0).getBody()).isEqualTo("Reasoning…");
        assertThat(revisions.get(1).getKind()).isEqualTo(NoteKind.THOUGHT);
    }

    @Test
    void staleExpectedVersionIsRejectedBeforeAnyWrite() {
        Note created = noteService.createNote("Guarded", "Current writing", NoteKind.NOTE);

        long wrongVersion = noteService.getNote(created.getId()).getVersion() + 7;

        assertThatThrownBy(() -> noteService.updateNote(created.getId(), "Stale edit",
                "Overwrite attempt", NoteKind.NOTE, wrongVersion))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // Newer server content survives; the stale draft overwrote nothing.
        Note reloaded = noteService.getNote(created.getId());
        assertThat(reloaded.getTitle()).isEqualTo("Guarded");
        assertThat(reloaded.getBody()).isEqualTo("Current writing");
        assertThat(noteRevisionRepository.countByNote_Id(created.getId())).isEqualTo(1);
        assertThat(noteService.listRevisions(created.getId()).get(0).getRevisionNumber())
                .isEqualTo(1);
    }

    @Test
    void oversizedBodyIsRejectedByValidationBeforeAnyDatabaseWrite() {
        Note created = noteService.createNote("Limit", "Safe body", NoteKind.NOTE);
        Note note = noteService.getNote(created.getId());

        // Exactly the cap is valid; one more character is rejected up front
        // (a clear 400) instead of surfacing as a database error later.
        noteService.updateNote(created.getId(), "Limit",
                "a".repeat(Note.MAX_BODY_LENGTH), NoteKind.NOTE, note.getVersion());
        assertThat(noteService.getNote(created.getId()).getBody())
                .hasSize(Note.MAX_BODY_LENGTH);

        long wrongVersion = noteService.getNote(created.getId()).getVersion();
        assertThatThrownBy(() -> noteService.updateNote(created.getId(), "Limit",
                "a".repeat(Note.MAX_BODY_LENGTH + 1), NoteKind.NOTE, wrongVersion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50000");

        Note reloaded = noteService.getNote(created.getId());
        assertThat(reloaded.getBody()).hasSize(Note.MAX_BODY_LENGTH);
        assertThat(noteRevisionRepository.countByNote_Id(created.getId())).isEqualTo(2);
    }

    @Test
    void noteAndRevisionsRollBackTogetherOnDatabaseFailure() {
        // A database-level rejection (duplicate revision number) inside the
        // service transaction must undo the note insert as well: no orphan
        // note, no orphan revision — they commit or roll back together.
        AtomicReference<UUID> noteId = new AtomicReference<>();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .execute(status -> {
                    Note note = noteService.createNote(
                            "Atomic unit", "Body", NoteKind.NOTE);
                    noteId.set(note.getId());
                    noteRevisionRepository.save(
                            new NoteRevision(note, 1, note.getCreatedAt()));
                    return null;
                }))
                .isInstanceOf(RuntimeException.class);

        assertThat(noteId.get()).isNotNull();
        assertThatThrownBy(() -> noteService.getNote(noteId.get()))
                .isInstanceOf(NotFoundException.class);
        assertThat(noteRevisionRepository.countByNote_Id(noteId.get())).isZero();
    }

    @Test
    void simultaneousEditsWithCorrectVersionsLetExactlyOneWin() throws Exception {
        Note created = noteService.createNote("Race", "Original", NoteKind.NOTE);
        UUID id = created.getId();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Object> outcomes;
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final String tag = "edit-" + i;
                results.add(pool.submit(() -> {
                    // Both editors read the SAME committed version, then are
                    // released together: the service-level version check
                    // passes for both (or loses to an already-committed
                    // winner), so the race must be closed by the row-level
                    // @Version — the loser must answer a VERSION CONFLICT,
                    // not any other error.
                    Note loaded = noteService.getNote(id);
                    barrier.await(5, TimeUnit.SECONDS);
                    try {
                        return (Object) noteService.updateNote(id, "Race " + tag,
                                "Body " + tag, NoteKind.NOTE, loaded.getVersion());
                    } catch (ObjectOptimisticLockingFailureException stale) {
                        return (Object) stale;
                    }
                }));
            }
            // Collect while the pool is still alive: shutdownNow would
            // interrupt the workers mid-transaction. Any unexpected worker
            // exception surfaces here and fails the test — only the two
            // legitimate outcomes (winner note, version conflict) may exist.
            outcomes = new ArrayList<>();
            outcomes.add(results.get(0).get(10, TimeUnit.SECONDS));
            outcomes.add(results.get(1).get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertThat(outcomes.stream().filter(o -> o instanceof Note).count())
                .as("exactly one editor wins").isEqualTo(1);
        assertThat(outcomes.stream()
                .filter(o -> o instanceof ObjectOptimisticLockingFailureException)
                .count())
                .as("the other editor answers a version conflict (HTTP 409)")
                .isEqualTo(1);

        Note reloaded = noteService.getNote(id);
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(reloaded.getLatestRevisionNumber()).isEqualTo(2);
        assertThat(noteRevisionRepository.countByNote_Id(id)).isEqualTo(2);
        assertThat(reloaded.getTitle()).startsWith("Race edit-");
        // The loser's content must not have landed anywhere: the stored body
        // is the winner's, and the loser's body appears in no revision.
        String winnerBody = reloaded.getBody();
        String loserBody = winnerBody.equals("Body edit-0")
                ? "Body edit-1"
                : "Body edit-0";
        assertThat(noteService.listRevisions(id))
                .extracting(NoteRevision::getBody)
                .doesNotContain(loserBody)
                .contains(winnerBody);
    }

    @Test
    void revisionHistoryPageIsBoundedNewestFirstAndDisjoint() {
        Note created = noteService.createNote("Paged", "v1", NoteKind.NOTE);
        UUID id = created.getId();
        for (int i = 2; i <= 8; i++) {
            noteService.updateNote(id, "Paged", "v" + i, null,
                    noteService.getNote(id).getVersion());
        }

        Page<NoteRevisionSummary> page0 = noteService.listRevisionPage(id, 0, 3);
        Page<NoteRevisionSummary> page1 = noteService.listRevisionPage(id, 1, 3);
        Page<NoteRevisionSummary> page2 = noteService.listRevisionPage(id, 2, 3);

        assertThat(page0.getTotalElements()).isEqualTo(8);
        assertThat(page0.getContent())
                .extracting(NoteRevisionSummary::getRevisionNumber)
                .containsExactly(8L, 7L, 6L);
        assertThat(page1.getContent())
                .extracting(NoteRevisionSummary::getRevisionNumber)
                .containsExactly(5L, 4L, 3L);
        assertThat(page2.getContent())
                .extracting(NoteRevisionSummary::getRevisionNumber)
                .containsExactly(2L, 1L);

        Page<NoteRevisionSummary> capped = noteService.listRevisionPage(id, 0, 500);
        assertThat(capped.getSize()).isEqualTo(100);
    }

    @Test
    void archiveAndRestorePreserveContentAndRevisions() {
        Note created = noteService.createNote("Vault", "Kept writing", NoteKind.NOTE);
        Note note = noteService.getNote(created.getId());

        Note archived = noteService.archiveNote(created.getId());
        assertThat(archived.isArchived()).isTrue();
        assertThat(archived.getBody()).isEqualTo("Kept writing");
        assertThat(noteRevisionRepository.countByNote_Id(created.getId())).isEqualTo(1);

        assertThatThrownBy(() -> noteService.archiveNote(created.getId()))
                .isInstanceOf(IllegalStateException.class);

        Note restored = noteService.restoreNote(created.getId());
        assertThat(restored.isArchived()).isFalse();
        assertThat(restored.getBody()).isEqualTo("Kept writing");
        assertThat(noteRevisionRepository.countByNote_Id(created.getId())).isEqualTo(1);
        // Archive churn must not fake a writing change.
        assertThat(noteService.getNote(created.getId()).getLatestRevisionNumber())
                .isEqualTo(1);
        assertThat(noteService.getNote(created.getId()).getVersion())
                .isGreaterThan(note.getVersion());
    }

    @Test
    void listNotesIsPaginatedCappedAndFilterable() {
        for (int i = 0; i < 8; i++) {
            noteService.createNote("Bulk " + i, "Body " + i, NoteKind.NOTE);
        }
        Note decision = noteService.createNote("Decision bulk", "Why", NoteKind.DECISION);
        noteService.archiveNote(decision.getId());

        Page<Note> page0 = noteService.listNotes(null, null, 0, 5);
        Page<Note> page1 = noteService.listNotes(null, null, 1, 5);
        assertThat(page0.getContent()).hasSize(5);
        assertThat(page0Ids(page0)).doesNotContainAnyElementsOf(page1Ids(page1));

        Page<Note> capped = noteService.listNotes(null, null, 0, 500);
        assertThat(capped.getSize()).isEqualTo(100);

        Page<Note> decisions = noteService.listNotes(NoteKind.DECISION, null, 0, 50);
        assertThat(decisions.getContent())
                .isNotEmpty()
                .allSatisfy(n -> assertThat(n.getKind()).isEqualTo(NoteKind.DECISION));

        Page<Note> archivedOnly = noteService.listNotes(null, true, 0, 50);
        assertThat(archivedOnly.getContent())
                .isNotEmpty()
                .allSatisfy(Note::isArchived)
                .extracting(Note::getId)
                .contains(decision.getId());

        Page<Note> activeOnly = noteService.listNotes(null, false, 0, 50);
        assertThat(page1Ids(activeOnly)).doesNotContain(decision.getId());
    }

    @Test
    void revisionsAreScopedToTheirNote() {
        Note noteA = noteService.createNote("Note A", "A body", NoteKind.NOTE);
        Note noteB = noteService.createNote("Note B", "B body", NoteKind.NOTE);

        UUID revisionOfB = noteService.listRevisions(noteB.getId()).get(0).getId();

        assertThatThrownBy(() -> noteService.getRevision(noteA.getId(), revisionOfB))
                .isInstanceOf(NotFoundException.class);

        assertThat(noteService.getRevision(noteA.getId(),
                noteService.listRevisions(noteA.getId()).get(0).getId()).getBody())
                .isEqualTo("A body");
    }

    @Test
    void unknownNoteAndRevisionAreRejected() {
        assertThatThrownBy(() -> noteService.getNote(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> noteService.listRevisions(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> noteService.updateNote(UUID.randomUUID(), "Nope", null,
                NoteKind.NOTE, 0))
                .isInstanceOf(NotFoundException.class);
    }

    private List<UUID> page0Ids(Page<Note> page) {
        return page.getContent().stream().map(Note::getId).toList();
    }

    private List<UUID> page1Ids(Page<Note> page) {
        return page.getContent().stream().map(Note::getId).toList();
    }
}
