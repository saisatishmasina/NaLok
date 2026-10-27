package com.shaktee.nalok.notes.web;

import com.shaktee.nalok.notes.model.NoteKind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Expected-version edit: the client sends the version it read; a mismatch
 * answers 409 and preserves the newer server content. expectedVersion is
 * mandatory — a silent blind write is not an option for notes. Null kind
 * keeps the current kind.
 */
public record UpdateNoteRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title cannot exceed 200 characters")
        String title,
        String body,
        NoteKind kind,
        @NotNull(message = "expectedVersion is required for edits")
        Long expectedVersion) {
}
