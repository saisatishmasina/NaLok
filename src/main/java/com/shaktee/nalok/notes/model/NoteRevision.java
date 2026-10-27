package com.shaktee.nalok.notes.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * Immutable snapshot of a note's writing at one explicit save. Creation
 * records revision 1; every content-changing save records another. Archive
 * state and timestamps live on the note, never here.
 *
 * Revisions answer "how did this piece of writing change" — they are not the
 * thinking trail (separate THOUGHT/DECISION/CHECKPOINT notes answer that).
 */
@Getter
@Entity
@Table(name = "note_revisions", uniqueConstraints = @UniqueConstraint(
        name = "uk_note_revisions_note_number",
        columnNames = { "note_id", "revision_number" }))
public class NoteRevision {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id", nullable = false, updatable = false)
    private Note note;

    @Column(name = "revision_number", nullable = false, updatable = false)
    private long revisionNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoteKind kind;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected NoteRevision() {
    }

    public NoteRevision(Note note, long revisionNumber, Instant recordedAt) {
        if (note == null) {
            throw new IllegalArgumentException("revision requires its note");
        }
        if (revisionNumber < 1) {
            throw new IllegalArgumentException("revision number starts at 1");
        }

        this.id = UUID.randomUUID();
        this.note = note;
        this.revisionNumber = revisionNumber;
        this.title = note.getTitle();
        this.body = note.getBody();
        this.kind = note.getKind();
        this.recordedAt = Objects.requireNonNull(recordedAt, "record time is required");
    }
}
