package com.shaktee.nalok.notes.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.shaktee.nalok.knowledge.study.model.StudySession;

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
 * Association note ↔ study session: "written while working on this".
 * Foreign key to the real session table, unique (note, session), unlink
 * removes only the row. A note linked to a session after the fact is still
 * reused content — linkedAt says when the association was made, nothing
 * more.
 */
@Getter
@Entity
@Table(name = "note_session_links", uniqueConstraints = @UniqueConstraint(
        name = "uk_note_session_links",
        columnNames = { "note_id", "session_id" }))
public class NoteSessionLink {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id", nullable = false, updatable = false)
    private Note note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private StudySession session;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    protected NoteSessionLink() {
    }

    public NoteSessionLink(Note note, StudySession session, Instant linkedAt) {
        if (note == null || session == null) {
            throw new IllegalArgumentException("link requires its note and session");
        }
        this.id = UUID.randomUUID();
        this.note = note;
        this.session = session;
        this.linkedAt = Objects.requireNonNull(linkedAt, "link time is required");
    }
}
