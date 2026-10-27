package com.shaktee.nalok.projects.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create a project. Description is optional; the model starts the
 * project in PLANNED state (starting it is an explicit lifecycle action).
 */
public record CreateProjectRequest(
        @NotBlank(message = "Project title is required")
        @Size(max = 200, message = "Project title cannot exceed 200 characters")
        String title,
        @Size(max = 10000)
        String description) {
}
