package com.shaktee.nalok.projects.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to edit a section's own fields (title + description). Position
 * changes are explicit reorder actions, never silently mixed into an edit.
 *
 * The version comes from the user's loaded snapshot; a stale version is a
 * 409 conflict, never a silent overwrite of newer work.
 */
public record UpdateSectionRequest(
        @NotBlank(message = "Section title is required")
        @Size(max = 200, message = "Section title cannot exceed 200 characters")
        String title,
        @Size(max = 10000)
        String description,
        @NotNull(message = "Section version is required")
        Long version) {
}
