package com.shaktee.nalok.sources.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Model-level rules for saved sources: URL identity (required, absolute,
 * http/https, size capped, stored verbatim), title/description bounds,
 * reversible archive. These run without Spring — they are the same rules
 * the database ultimately relies on.
 */
class SourceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    @Test
    void urlIsStoredVerbatimAndHashedExactly() {
        // Deliberately mixed case + port + path: stored letters are identity.
        String url = "http://Example.com:8443/a/Path?q=1";
        SavedSource source = new SavedSource("Ref", url, null, NOW);

        assertThat(source.getUrl()).isEqualTo(url);
        // Same letters → same identity key.
        assertThat(SavedSource.urlKeyFor(url)).isEqualTo(source.getUrlKey());
        // Different letters → different identity (no case folding).
        assertThat(SavedSource.urlKeyFor("http://example.com:8443/a/Path?q=1"))
                .isNotEqualTo(source.getUrlKey());
        assertThat(source.getUrlKey()).hasSize(64);
    }

    @Test
    void urlIsRequiredAndMustBeAbsoluteHttpOrHttps() {
        assertThatThrownBy(() -> new SavedSource("T", null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SavedSource("T", "   ", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SavedSource("T", "example.com/page", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute");
        assertThatThrownBy(() -> new SavedSource("T", "ftp://example.com", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http or https");
        assertThatThrownBy(() -> new SavedSource("T", "http:///no/host", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
        assertThatThrownBy(() -> new SavedSource("T", "http://ex ample.com", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> new SavedSource("T", "https://ok.com/a\u0007b", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void urlLengthIsCappedInModel() {
        // "https://x.com/" is 14 chars → total exactly 2048 is valid.
        String atCap = "https://x.com/" + "a".repeat(2034);
        assertThat(atCap).hasSize(2048);
        assertThat(SavedSource.urlKeyFor(atCap)).isNotNull();

        String over = atCap + "a";
        assertThatThrownBy(() -> new SavedSource("T", over, null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2048");
    }

    @Test
    void titleAndDescriptionBounds() {
        assertThatThrownBy(() -> new SavedSource(null, "https://x.com", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SavedSource("  ", "https://x.com", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> new SavedSource("t".repeat(201), "https://x.com", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);

        // Title is stripped; blank description becomes null; oversized rejected.
        SavedSource source = new SavedSource("  Padded  ", "https://x.com", "   ", NOW);
        assertThat(source.getTitle()).isEqualTo("Padded");
        assertThat(source.getDescription()).isNull();
        assertThatThrownBy(() -> source.changeContent("ok",
                "d".repeat(2001), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void archiveIsReversibleAndChangeContentStampsTime() {
        SavedSource source = new SavedSource("Paper", "https://x.com/a", "why", NOW);

        source.changeContent("Paper 2", null, NOW.plusSeconds(60));
        assertThat(source.getTitle()).isEqualTo("Paper 2");
        assertThat(source.getDescription()).isNull();
        assertThat(source.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));

        source.archive(NOW.plusSeconds(120));
        assertThat(source.isArchived()).isTrue();
        assertThatThrownBy(() -> source.archive(NOW))
                .isInstanceOf(IllegalStateException.class);

        // The URL is immutable: changeContent cannot touch it.
        source.restore(NOW.plusSeconds(180));
        assertThat(source.getUrl()).isEqualTo("https://x.com/a");
        assertThat(source.isArchived()).isFalse();
        assertThatThrownBy(() -> source.restore(NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
