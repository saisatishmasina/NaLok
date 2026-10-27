package com.shaktee.nalok.projects.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create a section under a project. Position is NOT caller data:
 * the service appends after the current last position (ordering is a UI
 * display concern, not an input the user types).
 */
public record CreateSectionRequest(
        @NotBlank(message = "Section title is required")
        @Size(max = 200, message = "Section title cannot exceed 200 characters")
        String title,
        @Size(max = 10000)
        String description) {
}
