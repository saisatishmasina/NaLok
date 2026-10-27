package com.shaktee.nalok.notes.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * An independent reusable note. It belongs to itself: study sessions,
 * projects and sections can reference it (N01b), but none of them owns it
 * and none of them may delete it with its content.
 *
 * The body is Markdown text. Content history is NOT stored here: every
 * content-changing save records an immutable {@link NoteRevision}, and
 * latestRevisionNumber tracks how many exist. That counter is deliberately
 * separate from the JPA {@code @Version}: archiving changes the record, but
 * must not pretend the writing changed.
 *
 * Body size is validated in the model (D11): MEDIUMTEXT storage with an
 * explicit character cap, so oversized writing is rejected by validation
 * and never surfaces as a database error.
 */
@Getter
@Entity
@Table(name = "notes")
public class Note {

    /** Explicit writing limit; MEDIUMTEXT has room far beyond it. */
    public static final int MAX_BODY_LENGTH = 50_000;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, length = 200)
    private String title;

    /**
     * Markdown body. Nullable: a note may start as a title placeholder.
     * Stored raw (never stripped) so multiline and code content survives.
     */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoteKind kind;

    /** Reversible archive state; restoring brings the note back untouched. */
    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Highest revision number recorded so far (1 after creation). */
    @Column(name = "latest_revision_number", nullable = false)
    private long latestRevisionNumber;

    protected Note() {
    }

    /**
     * @param kind null defaults to {@link NoteKind#NOTE}; explicit is better
     *             at call sites that know what they are writing.
     */
    public Note(String title, String body, NoteKind kind, Instant now) {
        this.id = UUID.randomUUID();
        this.title = validateTitle(title);
        this.body = validateBody(body);
        this.kind = kind == null ? NoteKind.NOTE : requireKind(kind);
        this.archived = false;
        this.createdAt = Objects.requireNonNull(now, "creation time is required");
        this.updatedAt = now;
        this.latestRevisionNumber = 0;
    }

    /**
     * Applies new content. Callers decide whether this was a content change
     * (compare against the previous values before calling) — the model only
     * validates and stores.
     */
    public void changeContent(String title, String body, NoteKind kind) {
        this.title = validateTitle(title);
        this.body = validateBody(body);
        this.kind = requireKind(kind);
    }

    public void archive(Instant now) {
        if (this.archived) {
            throw new IllegalStateException("note is already archived");
        }
        this.archived = true;
        this.updatedAt = Objects.requireNonNull(now, "archive time is required");
    }

    public void restore(Instant now) {
        if (!this.archived) {
            throw new IllegalStateException("note is not archived");
        }
        this.archived = false;
        this.updatedAt = Objects.requireNonNull(now, "restore time is required");
    }

    /** Number the next content revision will carry. */
    public long nextRevisionNumber() {
        return latestRevisionNumber + 1;
    }

    /** Called by the service once a revision row is persisted. */
    public void revisionRecorded(Instant recordedAt) {
        this.latestRevisionNumber++;
        this.updatedAt = Objects.requireNonNull(recordedAt, "record time is required");
    }

    private static String validateBody(String body) {
        if (body != null && body.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException(
                    "Note body cannot exceed " + MAX_BODY_LENGTH + " characters");
        }
        return body;
    }

    private static NoteKind requireKind(NoteKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("note kind is required");
        }
        return kind;
    }

    private static String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Note title cannot be empty");
        }

        String cleanedTitle = title.strip();

        if (cleanedTitle.length() > 200) {
            throw new IllegalArgumentException("Note title cannot exceed 200 characters");
        }

        return cleanedTitle;
    }
}
