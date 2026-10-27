package com.shaktee.nalok.notes.web;

import com.shaktee.nalok.notes.model.NoteKind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creation request. Body is optional (a title placeholder is a valid first
 * note); a null kind defaults to NOTE in the service.
 */
public record CreateNoteRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title cannot exceed 200 characters")
        String title,
        String body,
        NoteKind kind) {
}
