package com.shaktee.nalok.notes.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.shaktee.nalok.sources.model.SavedSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * Association note ↔ saved source, with a database-enforced foreign key on
 * both sides and a unique constraint against duplicate links. The link owns
 * only itself: unlinking removes this row and nothing else — never the note
 * and never the source. linkedAt records when the association was made; it
 * is NOT authorship provenance.
 */
@Getter
@Entity
@Table(name = "note_source_links", uniqueConstraints = @UniqueConstraint(
        name = "uk_note_source_links",
        columnNames = { "note_id", "source_id" }))
public class NoteSourceLink {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id", nullable = false, updatable = false)
    private Note note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false, updatable = false)
    private SavedSource source;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    protected NoteSourceLink() {
    }

    public NoteSourceLink(Note note, SavedSource source, Instant linkedAt) {
        if (note == null || source == null) {
            throw new IllegalArgumentException("link requires its note and source");
        }
        this.id = UUID.randomUUID();
        this.note = note;
        this.source = source;
        this.linkedAt = Objects.requireNonNull(linkedAt, "link time is required");
    }
}
