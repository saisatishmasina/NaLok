package com.shaktee.nalok.projects.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to edit a project's own fields (title + description). Lifecycle
 * changes (start/pause/complete/archive) are separate endpoint actions —
 * they have different rules and are never mixed into an edit call.
 *
 * The version comes from the user's loaded snapshot: submitting it proves
 * the edit was made against current state (stale form → 409, never a
 * silent overwrite of newer work).
 */
public record UpdateProjectRequest(
        @NotBlank(message = "Project title is required")
        @Size(max = 200, message = "Project title cannot exceed 200 characters")
        String title,
        @Size(max = 10000)
        String description,
        @NotNull(message = "Project version is required")
        Long version) {
}
