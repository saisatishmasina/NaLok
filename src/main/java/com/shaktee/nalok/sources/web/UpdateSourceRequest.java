package com.shaktee.nalok.sources.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Expected-version edit of title/description. No url field: the URL is the
 * source's identity and is not editable (D13). A mismatch answers 409 and
 * preserves the newer server content.
 */
public record UpdateSourceRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title cannot exceed 200 characters")
        String title,
        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description,
        @NotNull(message = "expectedVersion is required for edits")
        Long expectedVersion) {
}
