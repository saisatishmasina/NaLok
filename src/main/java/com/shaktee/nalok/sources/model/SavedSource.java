package com.shaktee.nalok.sources.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import lombok.Getter;

/**
 * A saved reference to a web location. R01a stores URL identity only —
 * no fetching, scraping or downloads; later reader slices extend this
 * record without replacing it.
 *
 * The URL is the source's identity: it is required, absolute http/https,
 * validated in the model, stored VERBATIM (never re-cased or re-encoded)
 * and immutable after creation — a mistyped address is corrected by
 * saving the right one, not by mutating history.
 *
 * Uniqueness is enforced on {@code url_key}, the SHA-256 hex of the exact
 * stored URL, because a unique index over VARCHAR(2048) would exceed
 * MySQL's utf8mb4 index-key limit. Exact-string uniqueness is deliberate:
 * without a fetching slice to confirm sameness, "same letters" is the only
 * honest definition of duplicate.
 */
@Getter
@Entity
@Table(name = "saved_sources", uniqueConstraints = @UniqueConstraint(
        name = "uk_saved_sources_url_key", columnNames = "url_key"))
public class SavedSource {

    /** Explicit address limit; storage is TEXT with room far beyond it. */
    public static final int MAX_URL_LENGTH = 2048;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String url;

    /** SHA-256 hex of the stored URL; the database-enforced identity. */
    @Column(name = "url_key", nullable = false, updatable = false, length = 64)
    private String urlKey;

    @Column(nullable = false, length = 200)
    private String title;

    /** Optional user annotation about why this place is worth keeping. */
    @Column(length = 2000)
    private String description;

    /** Reversible archive state; restoring brings the source back untouched. */
    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavedSource() {
    }

    public SavedSource(String title, String url, String description, Instant now) {
        this.id = UUID.randomUUID();
        this.title = validateTitle(title);
        this.urlKey = urlKeyFor(url);
        this.url = cleanedUrl(url);
        this.description = normalizeDescription(description);
        this.archived = false;
        this.createdAt = Objects.requireNonNull(now, "creation time is required");
        this.updatedAt = now;
    }

    /**
     * Validates and hashes the URL. Public because the service needs the
     * same identity value for its duplicate check before constructing.
     */
    public static String urlKeyFor(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(cleanedUrl(url).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is unavailable", missing);
        }
    }

    /**
     * Applies new title/description. The URL is NOT editable here — it is
     * the record's identity. Callers decide whether anything changed
     * (compare against the previous values); unchanged saves must not
     * move version or updatedAt.
     */
    public void changeContent(String title, String description, Instant now) {
        this.title = validateTitle(title);
        this.description = normalizeDescription(description);
        this.updatedAt = Objects.requireNonNull(now, "change time is required");
    }

    public void archive(Instant now) {
        if (this.archived) {
            throw new IllegalStateException("source is already archived");
        }
        this.archived = true;
        this.updatedAt = Objects.requireNonNull(now, "archive time is required");
    }

    public void restore(Instant now) {
        if (!this.archived) {
            throw new IllegalStateException("source is not archived");
        }
        this.archived = false;
        this.updatedAt = Objects.requireNonNull(now, "restore time is required");
    }

    private static String cleanedUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Source URL is required");
        }
        String cleaned = url.strip();
        if (cleaned.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException(
                    "Source URL cannot exceed " + MAX_URL_LENGTH + " characters");
        }
        // Whitespace/control characters are never valid inside a URL and
        // would otherwise silently change which page the address names.
        if (cleaned.chars().anyMatch(c -> c <= 32 || c == 127)) {
            throw new IllegalArgumentException(
                    "Source URL must not contain whitespace or control characters");
        }
        URI parsed;
        try {
            parsed = new URI(cleaned);
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("Source URL is not a valid URL");
        }
        if (!parsed.isAbsolute()) {
            throw new IllegalArgumentException(
                    "Source URL must be absolute (include http:// or https://)");
        }
        String scheme = parsed.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Source URL must be http or https");
        }
        if (parsed.getHost() == null || parsed.getHost().isBlank()) {
            throw new IllegalArgumentException("Source URL must include a host");
        }
        return cleaned;
    }

    private static String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Source title cannot be empty");
        }
        String cleanedTitle = title.strip();
        if (cleanedTitle.length() > 200) {
            throw new IllegalArgumentException("Source title cannot exceed 200 characters");
        }
        return cleanedTitle;
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String cleaned = description.strip();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > 2000) {
            throw new IllegalArgumentException(
                    "Source description cannot exceed 2000 characters");
        }
        return cleaned;
    }
}
