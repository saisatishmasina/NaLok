package com.shaktee.nalok;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TestDatabaseGuardTest {

    @Test
    void acceptsOnlyTheDisposableTestDatabase() {
        assertThatCode(() -> TestDatabaseGuard.assertDisposableDatabase(
                "jdbc:mysql://localhost:3306/nalok_opencode_test?createDatabaseIfNotExist=true"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsThePersonalDatabase() {
        assertThatThrownBy(() -> TestDatabaseGuard.assertDisposableDatabase(
                "jdbc:mysql://localhost:3306/nalok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nalok_opencode_test");
    }

    @Test
    void rejectsMissingUrlAndHalfMatches() {
        assertThatThrownBy(() -> TestDatabaseGuard.assertDisposableDatabase(null))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> TestDatabaseGuard.assertDisposableDatabase(
                "jdbc:mysql://localhost:3306/nalok_lookalike"))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> TestDatabaseGuard.assertDisposableDatabase(
                "jdbc:mysql://localhost:3306/"))
                .isInstanceOf(IllegalStateException.class);
    }
}
