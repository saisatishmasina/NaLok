# Verification strategy
Discover versions and project test configuration before copying any test annotations or dependencies.

## Test environment requirements
- Tests require DB_USERNAME and DB_PASSWORD in the environment (user provides via .env); committed test config holds placeholders only.
- Tests are hard-guarded to the disposable nalok_opencode_test database: TestDatabaseGuard (environment post-processor) rejects any other datasource BEFORE Hibernate initializes; TestDatabaseIsolationTest re-checks at runtime.
- Personal `nalok` database must never be used by tests; incident on 2026-10-26 recorded in STATUS.

## Per task
1. Establish existing relevant baseline.
2. Add tests for changed behavior and concrete failure risks.
3. Run focused tests; broaden only for affected integration boundaries.
4. Inspect Git diff and update task evidence.
5. Never claim unexecuted tests passed.

## Critical Study cases
Standalone session; linked project; linked section; nonexistent target; incomplete reference rejected.
Valid start/pause/resume/finish; invalid transitions; deterministic active duration; finish while paused.
Persistence round trip for absent/present references and timestamps.
Rollback when a required event write fails.
Concurrent updates if applicable: choose and verify lost-update protection.
Clarify and test target deletion policy rather than hiding dangling references.

## UI/desktop cases when implemented
Actual create-study-note-finish-resume flow; refresh/restart; unsaved changes and backend outage.
Missing/moved book; unresolved quote; unsupported website; blocked popup/permission.
Untrusted browser content cannot call privileged app functions.
Representative tab/document load and one bounded simulation later; record measurements instead of promising RAM use.

## Data
Disposable databases only. Use real MySQL integration tests for MySQL-specific mapping/constraints.
H2 success alone does not prove MySQL behavior. Testcontainers is optional if justified and available.
No tests against personal production data. Include a restore rehearsal before public release.

## Candidate commands (confirm first)
./mvnw compile
./mvnw test
./mvnw -Dtest=ActualTestClass test
./mvnw verify
Do not bypass test failures with skipped tests just to mark a task complete.
