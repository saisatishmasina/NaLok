package com.shaktee.nalok.sources.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shaktee.nalok.sources.model.SavedSource;
import com.shaktee.nalok.sources.repository.SavedSourceRepository;
import com.shaktee.nalok.web.NotFoundException;

/**
 * Complete saved-source operations and their transaction boundary. R01a is
 * URL identity only: no fetching, scraping or downloads.
 *
 * Duplicate URLs answer a conflict (HTTP 409): the URL is the record's
 * identity, so saving the same address twice is not a validation failure
 * but a collision with existing state. The race between two simultaneous
 * creates of the same URL is closed by the database unique constraint —
 * the loser's INSERT fails and is translated to the same conflict.
 */
@Service
@Transactional(readOnly = true)
public class SavedSourceService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SavedSourceRepository sourceRepository;
    private final Clock clock;

    public SavedSourceService(SavedSourceRepository sourceRepository, Clock clock) {
        this.sourceRepository = sourceRepository;
        this.clock = clock;
    }

    @Transactional
    public SavedSource createSource(String title, String url, String description) {
        // Validate/hash up front so the common duplicate case never reaches
        // an INSERT; the unique constraint still closes the true race.
        String urlKey = SavedSource.urlKeyFor(url);
        if (sourceRepository.existsByUrlKey(urlKey)) {
            throw new IllegalStateException("Source with this URL is already saved");
        }
        Instant now = clock.instant();
        try {
            // saveAndFlush: the INSERT (and its unique-key verdict) happens
            // HERE, inside the guarded window. A plain save() would defer the
            // insert to commit, where the constraint failure escapes this
            // catch and surfaces as a database error instead of the conflict.
            return sourceRepository.saveAndFlush(
                    new SavedSource(title, url, description, now));
        } catch (DataIntegrityViolationException rejected) {
            // Do NOT re-check with a query here: the still-open persistence
            // context would re-flush the failed INSERT and rethrow the same
            // database error. Verify the violated constraint by name instead;
            // any other integrity failure keeps its own (honest) exception.
            if (isDuplicateUrlKey(rejected)) {
                throw new IllegalStateException("Source with this URL is already saved");
            }
            throw rejected;
        }
    }

    private static boolean isDuplicateUrlKey(DataIntegrityViolationException rejected) {
        Throwable cause = rejected.getCause();
        if (!(cause instanceof org.hibernate.exception.ConstraintViolationException constraint)) {
            return false;
        }
        String name = constraint.getConstraintName();
        return name != null && name.contains("uk_saved_sources_url_key");
    }

    public SavedSource getSource(UUID sourceId) {
        return sourceRepository.findById(sourceId)
                .orElseThrow(() -> new NotFoundException("Source not found: " + sourceId));
    }

    public boolean sourceExists(UUID sourceId) {
        return sourceRepository.existsById(sourceId);
    }

    /**
     * Version-checked edit of title/description. The URL is not editable:
     * it is the identity (see SavedSource). An unchanged save records
     * nothing — version and updatedAt must not move for a no-op.
     */
    @Transactional
    public SavedSource updateSource(UUID sourceId, String title, String description,
            long expectedVersion) {
        SavedSource source = getSource(sourceId);
        if (source.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(SavedSource.class, sourceId);
        }

        if (!Objects.equals(source.getTitle(), title.strip())
                || !Objects.equals(source.getDescription(), normalize(description))) {
            source.changeContent(title, description, clock.instant());
        }
        return source;
    }

    @Transactional
    public SavedSource archiveSource(UUID sourceId) {
        SavedSource source = getSource(sourceId);
        source.archive(clock.instant());
        return source;
    }

    @Transactional
    public SavedSource restoreSource(UUID sourceId) {
        SavedSource source = getSource(sourceId);
        source.restore(clock.instant());
        return source;
    }

    /**
     * Bounded, newest-first page of source summaries for UI lists. Null
     * archived = both states; page size capped like the other modules.
     */
    public Page<SavedSource> listSources(Boolean archived, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageRequest = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")));

        if (archived == null) {
            return sourceRepository.findAll(pageRequest);
        }
        return sourceRepository.findByArchived(archived, pageRequest);
    }

    private static String normalize(String description) {
        if (description == null) {
            return null;
        }
        String cleaned = description.strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
