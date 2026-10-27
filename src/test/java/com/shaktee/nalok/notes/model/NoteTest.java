package com.shaktee.nalok.notes.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class NoteTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    @Test
    void createdNoteDefaultsToPlainNoteKindAndZeroRevisions() {
        Note note = new Note("First", "Body", null, NOW);

        assertThat(note.getKind()).isEqualTo(NoteKind.NOTE);
        assertThat(note.isArchived()).isFalse();
        assertThat(note.getCreatedAt()).isEqualTo(NOW);
        assertThat(note.getUpdatedAt()).isEqualTo(NOW);
        assertThat(note.getLatestRevisionNumber()).isZero();
        assertThat(note.nextRevisionNumber()).isEqualTo(1);
    }

    @Test
    void blankAndOversizedTitlesAreRejected() {
        assertThatThrownBy(() -> new Note("   ", "Body", NoteKind.NOTE, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Note(null, "Body", NoteKind.NOTE, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Note("x".repeat(201), null, NoteKind.NOTE, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
    }

    @Test
    void bodyIsStoredRawIncludingLeadingWhitespace() {
        String rawBody = "  indented line\n\nsecond paragraph\n\ttabbed";
        Note note = new Note("Raw", rawBody, NoteKind.NOTE, NOW);

        assertThat(note.getBody()).isEqualTo(rawBody);
    }

    @Test
    void changeContentValidatesAndAcceptsExplicitKind() {
        Note note = new Note("Before", "Body", NoteKind.NOTE, NOW);

        note.changeContent("After", "New body", NoteKind.DECISION);

        assertThat(note.getTitle()).isEqualTo("After");
        assertThat(note.getBody()).isEqualTo("New body");
        assertThat(note.getKind()).isEqualTo(NoteKind.DECISION);
        assertThatThrownBy(() -> note.changeContent("No kind", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void archiveAndRestoreAreReversibleAndGuarded() {
        Note note = new Note("Cycle", "Body", NoteKind.NOTE, NOW);

        note.archive(NOW.plusSeconds(60));
        assertThat(note.isArchived()).isTrue();
        assertThat(note.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThatThrownBy(() -> note.archive(NOW.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class);

        note.restore(NOW.plusSeconds(180));
        assertThat(note.isArchived()).isFalse();
        assertThat(note.getUpdatedAt()).isEqualTo(NOW.plusSeconds(180));
        assertThatThrownBy(() -> note.restore(NOW.plusSeconds(240)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revisionCounterAdvancesOnlyWhenRevisionRecorded() {
        Note note = new Note("Counted", "Body", NoteKind.NOTE, NOW);

        assertThat(note.nextRevisionNumber()).isEqualTo(1);
        note.revisionRecorded(NOW.plusSeconds(1));
        assertThat(note.getLatestRevisionNumber()).isEqualTo(1);
        assertThat(note.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(note.nextRevisionNumber()).isEqualTo(2);
    }
}
