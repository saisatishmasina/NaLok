package com.shaktee.nalok;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

/**
 * Test-classpath-only guard (registered in src/test/resources/META-INF/spring.factories).
 * Runs as an environment post-processor, i.e. BEFORE any bean — including the
 * EntityManagerFactory — is created. It rejects any datasource that does not
 * point at the disposable nalok_opencode_test database, so a misconfigured
 * test run cannot touch or create-drop tables in a personal database.
 */
public class TestDatabaseGuard implements EnvironmentPostProcessor, Ordered {

    static final String REQUIRED_DATABASE = "nalok_opencode_test";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        assertDisposableDatabase(environment.getProperty("spring.datasource.url"));
    }

    @Override
    public int getOrder() {
        // After config-data loading (application.properties), before beans.
        return Ordered.LOWEST_PRECEDENCE;
    }

    static void assertDisposableDatabase(String datasourceUrl) {
        if (datasourceUrl == null) {
            throw new IllegalStateException(
                    "Test guard: spring.datasource.url is not set. Tests must run "
                            + "against the disposable " + REQUIRED_DATABASE + " database.");
        }

        String database = databaseName(datasourceUrl);

        if (!REQUIRED_DATABASE.equals(database)) {
            throw new IllegalStateException(
                    "Test guard: refusing to run tests against database '" + database
                            + "'. Tests must use the disposable " + REQUIRED_DATABASE
                            + " database. Fix src/test/resources/application.properties.");
        }
    }

    private static String databaseName(String datasourceUrl) {
        int schemeEnd = datasourceUrl.indexOf("//");
        String afterHost = datasourceUrl.substring(
                datasourceUrl.indexOf('/', schemeEnd + 2) + 1);
        int queryStart = afterHost.indexOf('?');
        return queryStart >= 0 ? afterHost.substring(0, queryStart) : afterHost;
    }
}
