package com.shaktee.nalok.sources.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    void simultaneousDuplicateCreatesYieldExactlyOneRecordAndCleanConflict() throws Exception {
        String url = "https://race-create.test/one";
        long before = sourceService.listSources(null, 0, 1).getTotalElements();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Object> outcomes;
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final String tag = "create-" + i;
                results.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    try {
                        return (Object) sourceService.createSource("Race " + tag, url, null);
                    } catch (IllegalStateException conflict) {
                        return (Object) conflict;
                    }
                }));
            }
            // Collect while the pool is still alive; any unexpected failure
            // surfaces here and fails the test.
            outcomes = new ArrayList<>();
            outcomes.add(results.get(0).get(10, TimeUnit.SECONDS));
            outcomes.add(results.get(1).get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(outcomes.stream().filter(o -> o instanceof SavedSource).count())
                .as("exactly one creator wins").isEqualTo(1);
        assertThat(outcomes.stream().filter(o -> o instanceof IllegalStateException).count())
                .as("the loser answers the URL conflict (HTTP 409)").isEqualTo(1);
        assertThat(sourceService.listSources(null, 0, 1).getTotalElements())
                .as("exactly one record was persisted")
                .isEqualTo(before + 1);
    }

    @Test
    void simultaneousEditsWithSameVersionLetExactlyOneWin() throws Exception {
        SavedSource created = sourceService.createSource(
                "Edit race", "https://race-edit.test/one", null);
        UUID id = created.getId();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Object> outcomes;
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final String tag = "edit-" + i;
                results.add(pool.submit(() -> {
                    // Both editors read the SAME committed version, then are
                    // released together: with no second table there is no
                    // lock-order cycle, so the row-level @Version alone must
                    // produce exactly one winner and one clean conflict.
                    SavedSource loaded = sourceService.getSource(id);
                    barrier.await(5, TimeUnit.SECONDS);
                    try {
                        return (Object) sourceService.updateSource(id,
                                "Race " + tag, "Body " + tag, loaded.getVersion());
                    } catch (ObjectOptimisticLockingFailureException stale) {
                        return (Object) stale;
                    }
                }));
            }
            outcomes = new ArrayList<>();
            outcomes.add(results.get(0).get(10, TimeUnit.SECONDS));
            outcomes.add(results.get(1).get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(outcomes.stream().filter(o -> o instanceof SavedSource).count())
                .as("exactly one editor wins").isEqualTo(1);
        assertThat(outcomes.stream()
                .filter(o -> o instanceof ObjectOptimisticLockingFailureException).count())
                .as("the loser answers a version conflict (HTTP 409)").isEqualTo(1);

        SavedSource reloaded = sourceService.getSource(id);
        assertThat(reloaded.getTitle()).startsWith("Race edit-");
        assertThat(reloaded.getVersion()).isEqualTo(1);
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
