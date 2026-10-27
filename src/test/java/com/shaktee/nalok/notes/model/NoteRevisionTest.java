package com.shaktee.nalok.notes.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class NoteRevisionTest {

    private static final Instant NOW = Instant.parse("2026-10-26T11:00:00Z");

    @Test
    void revisionSnapshotsTheNoteWritingAtSaveTime() {
        Note note = new Note("Snapshot", "Body", NoteKind.QUESTION, NOW);

        NoteRevision revision = new NoteRevision(note, 1, NOW.plusSeconds(5));

        assertThat(revision.getNote()).isSameAs(note);
        assertThat(revision.getRevisionNumber()).isEqualTo(1);
        assertThat(revision.getTitle()).isEqualTo("Snapshot");
        assertThat(revision.getBody()).isEqualTo("Body");
        assertThat(revision.getKind()).isEqualTo(NoteKind.QUESTION);
        assertThat(revision.getRecordedAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void invalidRevisionRequestsAreRejected() {
        Note note = new Note("Valid", "Body", NoteKind.NOTE, NOW);

        assertThatThrownBy(() -> new NoteRevision(null, 1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NoteRevision(note, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NoteRevision(note, 1, null))
                .isInstanceOf(NullPointerException.class);
    }
}
