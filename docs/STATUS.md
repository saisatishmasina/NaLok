# Status — audited 2026-10-26

## Reported by user, not repository-verified
- Root Java package: com.shaktee.nalok.
- Projects model/repository/service implemented; MySQL dependency/configuration discussed.
- Study design in progress. Latest implemented Study code is unknown.
- Shared optional EntityReference concept accepted for Study/Notes.
- No verified frontend or desktop implementation.
- Historical branch name feat/world-events was mentioned; do not assume current branch.

## Verified locally
- Current branch during Projects work: feat/Projects (worktree branch projects-work, pushed to origin/feat/Projects).
- Java 21.0.13.0.2 (Homebrew); Spring Boot 4.1.1; Maven 3.9.16 via wrapper; MySQL 8.0.46 local server.
- Database config uses ${DB_USERNAME}/${DB_PASSWORD} placeholders resolved from environment (user provides .env; credentials not stored in repo).
- Tests run against disposable MySQL database nalok_opencode_test (create-drop), configured in committed src/test/resources/application.properties (placeholders only, no secrets); real credentials come from the environment.
- No frontend, desktop UI, controllers, Notes or EntityReference implementation exists yet.

## Record after audit
- Current branch and existing uncommitted changes: feat/Projects had modified ProjectService/ProjectSectionService and untracked study/world-event scaffolding and docs.
- Java / Spring Boot / Hibernate versions: Java 21; Spring Boot 4.1.1 (Jakarta, Hibernate via Boot 4.1.1); MySQL connector-j.
- Build and test commands: `./mvnw compile`, `./mvnw test`, `./mvnw verify` (all confirmed working).
- Existing modules and verified functionality: Projects models/repositories/services (see tests); world/event record plus empty repository/service; study entity incomplete (has compile blockers, moved to Study task).
- Database/migration setup: no migration files; dev uses ddl-auto=update on database `nalok`; tests use disposable database `nalok_opencode_test` with ddl-auto=create-drop.
- Baseline compile/test result: 30/30 tests pass (see below).
- Known limitations: enum value spelled ACIVE (persisted; kept this milestone); no REST layer; no optimistic locking on Project yet; Study/WorldEvent repository and service files empty.

## Incident record — 2026-10-26 (resolved)
- The first Study test run executed against the personal `nalok` database because the test configuration was left in the Projects worktree. It created a `studysessions` table and inserted 20 project, 12 section and 18 session test rows.
- Verified (read-only) that every affected row was from that run: zero rows outside the test-time window. Cleaned up: rows deleted, table dropped. The `nalok` database has 0 project/section rows, as before.
- Prevented permanently: committed credential-free test config (placeholders resolved from env) plus TestDatabaseIsolationTest, which fails if tests run against anything but nalok_opencode_test.

## Latest completed task
Projects backend milestone (pushed to origin/feat/Projects, commit c02986c): fixed start() guard, createProject persistence, archive double-archive guard.

Study S01+S02 (local commit 7e6e10a, then hardening fixes): EntityReference embeddable context with round trips; lifecycle with explicit-Instant transitions, correct elapsed math, @Version; single-focused-session rule enforced with a MySQL named-lock guard released after commit (D08); backward-dated resume/finish rejected via paused_at; test isolation guard added.

## Verification evidence (current)
- Fresh `./mvnw verify`: BUILD SUCCESS, Tests run: 63, Failures: 0, Errors: 0, Skipped: 0 (no leftover intentional-failure reports).
- `git diff --check`: clean.
- Test reports confirm all Spring contexts connect to nalok_opencode_test only.
- Guard ordering proven: a forced misconfigured run (`-Dspring.datasource.url=.../nalok`) failed in 0.3s at environment-postprocessing, before Hibernate, leaving the personal database untouched (no tables created).
- Guard release proven cross-connection: after commit and after rollback, IS_USED_LOCK on a separate physical connection returns NULL (reentrancy-safe check).

## Study progress (feat/Study)
- S01 EntityReference embeddable mapped (context_type/context_id), both-or-neither enforced; MySQL round trips for absent, project and section contexts.
- S02 StudySession lifecycle with explicit-Instant transitions, correct elapsed math, @Version optimistic locking, auto-pause of previous ACTIVE session (D06), recoverInterrupted (D07), paused_at chronology checks, named-lock guard for concurrent starts (D08).
- Known limitations: Project lacks @Version; ACIVE enum spelling retained; recoverInterrupted counts crash gap as active time; test guard permits only nalok_opencode_test (by design).

## Next task
S03 Study persistence/service hardening (pagination, optimistic-lock conflict test, context validation review), then S04 REST slice.
