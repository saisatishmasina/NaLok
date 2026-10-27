package com.shaktee.nalok.sources.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creation request. Description is optional; URL validity and scheme are
 * enforced in the model so the same rules hold everywhere.
 */
public record CreateSourceRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title cannot exceed 200 characters")
        String title,
        @NotBlank(message = "Source URL is required")
        @Size(max = 2048, message = "Source URL cannot exceed 2048 characters")
        String url,
        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description) {
}
