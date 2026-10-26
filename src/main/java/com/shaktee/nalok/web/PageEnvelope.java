package com.shaktee.nalok.web;

import java.util.List;

/**
 * Stable pagination envelope so the UI never depends on Spring's Page
 * serialization format.
 */
public record PageEnvelope<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageEnvelope<T> of(List<T> content, int page, int size,
            long totalElements, int totalPages) {
        return new PageEnvelope<>(content, page, size, totalElements, totalPages);
    }
}
