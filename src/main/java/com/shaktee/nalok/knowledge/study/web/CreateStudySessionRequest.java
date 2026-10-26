package com.shaktee.nalok.knowledge.study.web;

import java.util.UUID;

import com.shaktee.nalok.world.reference.EntityType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to create a planned study session. Context is optional: a null
 * (absent) context creates a standalone session; a present context must name
 * a supported entity type and carry the target id (both-or-neither).
 */
public record CreateStudySessionRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title cannot exceed 200 characters")
        String title,
        @Size(max = 10000)
        String summary,
        @Valid
        ContextRequest context) {

    public record ContextRequest(
            @NotNull(message = "Context type is required when context is given")
            EntityType entityType,
            @NotNull(message = "Context id is required when context is given")
            UUID referenceId) {
    }
}
