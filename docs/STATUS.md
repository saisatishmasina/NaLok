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
- Tests run against disposable MySQL database nalok_opencode_test (create-drop), configured in gitignored src/test/resources/application.properties; credentials resolved from user-provided .env placeholders (never stored in repo).
- No frontend, desktop UI, controllers, Notes or EntityReference implementation exists yet.

## Record after audit
- Current branch and existing uncommitted changes: feat/Projects had modified ProjectService/ProjectSectionService and untracked study/world-event scaffolding and docs.
- Java / Spring Boot / Hibernate versions: Java 21; Spring Boot 4.1.1 (Jakarta, Hibernate via Boot 4.1.1); MySQL connector-j.
- Build and test commands: `./mvnw compile`, `./mvnw test`, `./mvnw verify` (all confirmed working).
- Existing modules and verified functionality: Projects models/repositories/services (see tests); world/event record plus empty repository/service; study entity incomplete (has compile blockers, moved to Study task).
- Database/migration setup: no migration files; dev uses ddl-auto=update on database `nalok`; tests use disposable database `nalok_opencode_test` with ddl-auto=create-drop.
- Baseline compile/test result: 30/30 tests pass (see below).
- Known limitations: enum value spelled ACIVE (persisted; kept this milestone); no REST layer; no optimistic locking on Project yet; Study/WorldEvent repository and service files empty.

## Latest completed task
Projects backend milestone (pushed to origin/feat/Projects, commit c02986c): fixed start() guard, createProject persistence, archive double-archive guard; 30 tests covering model rules, service lifecycle and real-MySQL persistence. Verification: `./mvnw test` and `./mvnw verify` BUILD SUCCESS (30/30), `git diff --check` clean.

## Study progress (feat/Study, local)
- S01 EntityReference embeddable mapped (context_type/context_id), both-or-neither enforced; MySQL round trips for absent, project and section contexts.
- S02 StudySession lifecycle complete with explicit-Instant transitions, correct elapsed math, @Version optimistic locking, auto-pause of previous ACTIVE session (D06) and recoverInterrupted (D07).
- Verification: `./mvnw test` and `./mvnw verify` BUILD SUCCESS (54/54: 30 Projects regression + 24 new), `git diff --check` clean.

## Next task
S03 Study persistence/service hardening (pagination, optimistic-lock conflict test, context validation review), then S04 REST slice.
