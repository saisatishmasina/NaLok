package com.shaktee.nalok;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Guarantees test isolation: every Spring test context must point at the
 * disposable test database, never at a personal database. If this test fails,
 * fix src/test/resources/application.properties before running anything else.
 */
@SpringBootTest
class TestDatabaseIsolationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testsRunOnlyAgainstTheDisposableDatabase() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);

        assertThat(database)
                .as("Spring tests must use the disposable database")
                .isEqualTo("nalok_opencode_test");
    }
}
