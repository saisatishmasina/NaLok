package com.shaktee.nalok.sources.web;

import java.time.Instant;
import java.util.UUID;

import com.shaktee.nalok.sources.model.SavedSource;

/**
 * Bounded API representations of saved sources. Never expose the JPA
 * entities directly. The version is exposed on purpose: the next PUT must
 * send it back as expectedVersion or be rejected with 409.
 */
public record SourceDto(
        UUID id,
        String url,
        String title,
        String description,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static SourceDto of(SavedSource source) {
        return new SourceDto(source.getId(), source.getUrl(), source.getTitle(),
                source.getDescription(), source.isArchived(), source.getCreatedAt(),
                source.getUpdatedAt(), source.getVersion());
    }

    /** List payload: same shape — a source IS its url, so pages keep it. */
    public record SourceSummaryDto(
            UUID id,
            String url,
            String title,
            boolean archived,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        public static SourceSummaryDto of(SavedSource source) {
            return new SourceSummaryDto(source.getId(), source.getUrl(),
                    source.getTitle(), source.isArchived(), source.getCreatedAt(),
                    source.getUpdatedAt(), source.getVersion());
        }
    }
}
