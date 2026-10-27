package com.shaktee.nalok.notes.web;

import java.time.Instant;
import java.util.UUID;

import com.shaktee.nalok.notes.model.Note;
import com.shaktee.nalok.notes.model.NoteKind;
import com.shaktee.nalok.notes.model.NoteRevision;

/**
 * Bounded API representations of notes. Never expose the JPA entities
 * directly. The version is exposed on purpose: the next PUT must send it
 * back as expectedVersion or be rejected with 409.
 */
public record NoteDto(
        UUID id,
        String title,
        String body,
        NoteKind kind,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long latestRevisionNumber,
        long version) {

    public static NoteDto of(Note note) {
        return new NoteDto(note.getId(), note.getTitle(), note.getBody(),
                note.getKind(), note.isArchived(), note.getCreatedAt(),
                note.getUpdatedAt(), note.getLatestRevisionNumber(),
                note.getVersion());
    }

    /** List payload: same shape minus the body, so pages stay small. */
    public record NoteSummaryDto(
            UUID id,
            String title,
            NoteKind kind,
            boolean archived,
            Instant createdAt,
            Instant updatedAt,
            long latestRevisionNumber,
            long version) {

        public static NoteSummaryDto of(Note note) {
            return new NoteSummaryDto(note.getId(), note.getTitle(),
                    note.getKind(), note.isArchived(), note.getCreatedAt(),
                    note.getUpdatedAt(), note.getLatestRevisionNumber(),
                    note.getVersion());
        }
    }

    public record NoteRevisionDto(
            UUID revisionId,
            long revisionNumber,
            String title,
            String body,
            NoteKind kind,
            Instant recordedAt) {

        public static NoteRevisionDto of(NoteRevision revision) {
            return new NoteRevisionDto(revision.getId(),
                    revision.getRevisionNumber(), revision.getTitle(),
                    revision.getBody(), revision.getKind(),
                    revision.getRecordedAt());
        }
    }
}
