package com.shaktee.nalok.sources.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.shaktee.nalok.sources.model.SavedSource;
import com.shaktee.nalok.web.NotFoundException;

/**
 * Persistence-level behavior for saved sources: URL identity survives
 * reload verbatim, duplicate URLs are a conflict (not a validation error),
 * edits are version-checked with no-op saves leaving no trace, and
 * archive/restore are reversible.
 */
@SpringBootTest
class SavedSourceServiceIntegrationTest {

    @Autowired
    private SavedSourceService sourceService;

    @Test
    void urlSurvivesReloadVerbatim() {
        String url = "https://example.org/docs/Guide?section=2#anchored";
        SavedSource created = sourceService.createSource(
                "Spring guide", url, "Reference for behavior X");

        SavedSource reloaded = sourceService.getSource(created.getId());
        assertThat(reloaded.getUrl()).isEqualTo(url);
        assertThat(reloaded.getTitle()).isEqualTo("Spring guide");
        assertThat(reloaded.getDescription()).isEqualTo("Reference for behavior X");
        assertThat(reloaded.isArchived()).isFalse();
        assertThat(reloaded.getUrlKey())
                .isEqualTo(SavedSource.urlKeyFor(url));
    }

    @Test
    void duplicateUrlIsAConflictEvenWithDifferentTitle() {
        String url = "https://duplicate.test/one";
        SavedSource first = sourceService.createSource("First", url, null);

        assertThatThrownBy(() -> sourceService.createSource("Second", url, "other"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already saved");

        // Same letters in a different case is ALSO stored letters — and the
        // verbatim-identity rule (D13) makes it a distinct record.
        SavedSource other = sourceService.createSource("Second",
                "https://Duplicate.test/one", null);
        assertThat(other.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void unchangedEditLeavesNoTraceChangedEditMovesVersion() {
        SavedSource created = sourceService.createSource(
                "Edit me", "https://edit.test/a", "before");
        long versionBefore = created.getVersion();
        UUID id = created.getId();

        // Unchanged (description only differs by surrounding whitespace →
        // normalizes to the same stored value): nothing may move.
        SavedSource untouched = sourceService.updateSource(id, "Edit me",
                "  before  ", versionBefore);
        assertThat(untouched.getVersion()).isEqualTo(versionBefore);
        assertThat(untouched.getUpdatedAt()).isEqualTo(created.getUpdatedAt());

        SavedSource changed = sourceService.updateSource(id, "Edited title",
                "after", untouched.getVersion());
        assertThat(changed.getVersion()).isEqualTo(versionBefore + 1);
        assertThat(changed.getTitle()).isEqualTo("Edited title");
        assertThat(changed.getUrl()).isEqualTo("https://edit.test/a");
    }

    @Test
    void staleExpectedVersionIsRejectedAndKeepsNewerContent() {
        SavedSource created = sourceService.createSource(
                "Guarded", "https://guard.test/a", null);
        long staleVersion = created.getVersion();
        sourceService.updateSource(created.getId(), "Newer truth", "newer",
                staleVersion);

        assertThatThrownBy(() -> sourceService.updateSource(created.getId(),
                "Stale edit", "stale", staleVersion))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        SavedSource reloaded = sourceService.getSource(created.getId());
        assertThat(reloaded.getTitle()).isEqualTo("Newer truth");
        assertThat(reloaded.getDescription()).isEqualTo("newer");
    }

    @Test
    void archiveAndRestoreAreReversibleAndFiltersWork() {
        SavedSource kept = sourceService.createSource(
                "Kept", "https://filter.test/kept", null);
        SavedSource archived = sourceService.createSource(
                "Archived", "https://filter.test/gone", null);

        sourceService.archiveSource(archived.getId());
        assertThatThrownBy(() -> sourceService.archiveSource(archived.getId()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(sourceService.listSources(false, 0, 10).getContent())
                .extracting(SavedSource::getId).contains(kept.getId())
                .doesNotContain(archived.getId());
        assertThat(sourceService.listSources(true, 0, 10).getContent())
                .extracting(SavedSource::getId).containsExactly(archived.getId());

        // Relative, not absolute totals: other tests in this class share the
        // context database, so only membership can be asserted here.
        Page<SavedSource> all = sourceService.listSources(null, 0, 100);
        assertThat(all.getContent())
                .extracting(SavedSource::getId)
                .contains(kept.getId(), archived.getId());

        sourceService.restoreSource(archived.getId());
        assertThat(sourceService.getSource(archived.getId()).isArchived()).isFalse();
        assertThatThrownBy(() -> sourceService.restoreSource(archived.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownSourceIsRejectedAndListIsBounded() {
        assertThatThrownBy(() -> sourceService.getSource(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);

        for (int i = 1; i <= 5; i++) {
            sourceService.createSource("Bulk " + i, "https://bulk.test/" + i, null);
        }
        Page<SavedSource> page = sourceService.listSources(null, 0, 3);
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(5);

        Page<SavedSource> capped = sourceService.listSources(null, 0, 500);
        assertThat(capped.getSize()).isEqualTo(100);
    }
}
