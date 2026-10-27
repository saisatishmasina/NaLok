# Status — audited 2026-10-26

## R01a Saved Sources (feat/Sources, 2026-10-26)
- URL-only first slice implemented: `com.shaktee.nalok.sources` (SavedSource
  model with in-model URL validation, SHA-256 `url_key` unique identity,
  version-checked edits, reversible archive, bounded lists; note_source_links
  with DB-enforced FKs under the existing per-note guard; /api/sources REST +
  note↔source link endpoints). NO fetching/scraping/downloads.
- Verification: `sh mvnw verify` → BUILD SUCCESS, Tests run: 156, Failures: 0,
  Errors: 0 (feat/Notes baseline 140). `git diff --check` clean.
- D13 records the identity rules: URL immutable, verbatim (no case
  normalization), duplicate = 409, case-different URLs = distinct records.

## N02 follow-up correction pass (feat/Notes, 2026-10-26)
- Audit of the reported-complete N02 found four real gaps; all closed:
  (1) the concurrent-edit test did not actually prove the loser receives a
  version conflict — rebuilt with exact outcome assertions, which exposed a
  REAL InnoDB deadlock, fixed by deterministic lock ordering (D12);
  (2) context retrieval was unbounded → DB-side paged queries + page
  endpoints for project/section/session notes; (3) revision pages loaded
  bodies → closed `NoteRevisionSummary` projection; (4) note-link named-lock
  release had no cross-connection proof → IS_USED_LOCK probes after commit
  and rollback added.
- Verification: `sh mvnw verify` → BUILD SUCCESS, Tests run: 140,
  Failures: 0, Errors: 0 (baseline was 136). `git diff --check` clean.
- Attribution correction: the earlier cross-session DB incident is no longer
  attributed to the frontend session by the missing-table evidence alone; it
  is recorded only as "concurrent worktree test runs can destroy each other's
  schema — never run both suites simultaneously".
- Saved Sources (R01a): NOT started.

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

## Frontend milestone (feat/Study, 2026-10-26)
- Baseline re-verified: previous run documented 63 tests; repository inspection found 67 existing tests (pagination, optimistic-lock tests already done — S03 leftovers), all passing.
- S02.5 work intervals added: StudyWorkInterval (session_id, started_at, ended_at open while ACTIVE), maintained in the SAME transaction as every lifecycle transition including D06 auto-pause and recoverInterrupted. Period sums overlap-window math per session and globally; open interval capped at min(now, windowEnd). Coverage limitation: sessions started before intervals existed have no historical breakdown; UI must not fabricate history.
- F02 hardening: zone-aware weekly summary (Monday-based, 7 daily buckets, DST-safe via local-midnight conversion, America/Chicago spring-forward proven 1h/2h with no lost time); recoverInterrupted closes the open leg AT the recovery instant (asserted exactly); rollback atomicity proven — a failed resume leaves every session and interval write undone; days entirely before coverage are uncovered+zero, never fabricated zeroes.
- F03 Core REST (verified): /api/study/sessions CRUD-lifecycle (create/start/pause/resume/finish/recover-interrupted), current-session endpoint (200/204), history page envelope, /api/study/summary?zone=; read-only /api/projects (+summary, sections). DTOs only (StudySessionDto computes live activeSecondsTotal; persisted ACIVE presented as ACTIVE at the boundary). Error mapping: ProblemDetail with 400/404/409/503 (GuardUnavailableException); @AutoConfigureMockMvc integration tests cover statuses and error bodies.
- Verified: `./mvnw verify` → Tests run: 95, Failures: 0, Errors: 0 (13 interval + 9 study-API + 6 projects-API + 67 prior). `./mvnw compile` and `git diff --check` clean.
- Notes: spring-boot-starter-validation added (managed version); Spring Boot 4.1 relocated @AutoConfigureMockMvc to org.springframework.boot.webmvc.test.autoconfigure; blank-title 400 comes from model validation, asserting an optional ProblemDetail `type` field would fail — assert title/detail. Hibernate create-drop logs a harmless FK drop-order warning for project_sections at context shutdown (timestamped WARN, not a test failure).
- Known test-env quirk: .env uses `KEY = value` spacing; plain `source .env` fails — load with a normalized parse (no secrets committed, none printed).

## F03 hardening pass (feat/Study, 2026-10-26) — re-audit of the above
- Review found six gaps; all closed: (1) request validation was inert — @Valid added on study create with cascade into the nested context record; (2) coverage anchor was the moving earliest interval — replaced by a persisted rollout marker (studyreportingcoverage singleton, written transactionally with the first leg) with coverageStart = min(marker, earliest interval), plus a future-day rule: days after "now" are never covered; (3) rollback test rewritten so the D06 auto-pause + interval close genuinely precede the failing guard — atomicity is now actually exercised (an earlier timestep failed before any writes and proved nothing); (4) periodic sums use lean span projections instead of dragging entities into memory (SQL-side aggregation deferred: native Instant binding risks connection-timezone mismatch); (5) StudySessionDto now carries serverNow to anchor the live timer (alongside activeSecondsTotal); (6) dangling context targets → 404 "Context target not found", malformed bodies and bad parameter types → 400 ProblemDetail.
- Verified: `./mvnw test` → BUILD SUCCESS, Tests run: 95, Failures: 0, Errors: 0. `./mvnw verify -DskipTests` clean, `git diff --check` clean.
- One intermediate run failed with cross-test pollution (bad timestep left a committed ACTIVE session); fixed in the test itself — production code unchanged by that incident.
- Node v22.14.0 / npm 10.9.2 confirmed for the F04 scaffold.

## F04 frontend slice (feat/Study, 2026-10-26)
- Scaffolded `frontend/` with Vite 8 + React 19 + TS strict; `npm run build` and `oxlint` clean (one documented fetch-in-effect warning remains, alive-guarded).
- Design tokens: brand palette #16C47F/#FFD65A/#FF9D23/#F93827 over a deep glass system (translucent layers, backdrop blur, hairline strokes, ambient brand glows); glass sidebar with Home/Projects/Study; routes /, /projects, /projects/:id, /study.
- API layer mirrors backend DTOs (StudySessionDto incl. serverNow anchor, StudyWeeklySummary, ProjectSummary, PageEnvelope) with ProblemDetail-aware errors; 204 handled as a value; Vite dev proxy /api → :8080 keeps everything same-origin for the desktop packaging later.
- Home dashboard is fully real-data: active projects summary card, current-study card with drift-anchored live timer (negative/uncovered states honest), weekly focus card with 7 day bars where covered=false renders as hatched "no reliable data" (never a fabricated 0) plus "per-leg data since" coverage note; browser zone is the summary zone.
- Projects read-only list + detail (status filters, paged, sections) and a Study workspace first slice (focused session with start/pause/resume/finish, recent history) shipped with explicit read-only/empty-state notices instead of fake content.
- Verified end-to-end: Spring (dev, personal nalok DB — GET-only exercise) + Vite dev through the proxy: projects summary 200, current 204, Chicago-zone summary 7 uncovered/zero buckets with coverageStart null, index 200. Frontend dist build clean.
- Note: running the dev server applied F02/F03 schema (studyreportingcoverage, studysessions, studyworkintervals, projects.version) to the personal `nalok` database via ddl-auto=update on first run — normal dev-path behavior, no test rows written.

## F04 review-correction pass (feat/Study, 2026-10-26)
- Re-checking the reported-complete F04 found SIX real defects; every claim below was verified in code, not assumed: (1) Home rendered `204` "no current session" as a perpetual loading state; (2) PLANNED/PAUSED sessions had no reachable Start/Resume after pausing; (3) the live timer used `Date.now() − serverNow` arithmetic (clock-skew sensitive) and the Study timer had no ticking loop; (4) the TS API mirrors diverged from the backend enums (`SECTION`, missing `PLANNED`, invented `INTERRUPTED`); (5) a day whose end equals the coverage instant was still marked covered; (6) sessions already ACTIVE before interval tracking lost their ongoing leg (no rollout bridge).
- All six fixed. Frontend: nullable-empty states separated from loading; PLANNED/PAUSED lifecycle controls reachable in history rows; in-place refresh via a refresh key (no more page reloads); skew-immune monotonic timer (`performance.now` anchored per snapshot, re-anchored on refresh, pinned by 10 new vitest tests); enum mirrors corrected (+PLANNED filter chip and styles). Backend: strict `coverageStart.isBefore(dayEnd)` day rule; marker-gated rollout bridge recording [activeSince, now] exactly once for pre-rollout ACTIVE sessions.
- Verification: `./mvnw test` → 97/0 (bridge + boundary tests intentionally live in separate test classes so each gets a FRESH create-drop context database — the shared-DB pollution class of bug is avoided by construction, after an intermediate run reproduced that exact bug pattern from the new tests themselves); `npm run build` clean; `vitest run` 10/10; `oxlint` → 2 documented inherent-pattern warnings (fetch-in-effect, anchor-in-effect); `git diff --check` clean.
- Honest remainders: no browser/interaction tests yet (Playwright is an open decision, not silently added); aggregation still happens in Java (span projections only — SQL-side aggregation remains deferred with its documented timezone-binding reason); committing/pushing feat/Study is still pending the user's decision.

## Verification evidence (current)
- N02 (feat/Notes): `sh mvnw verify` BUILD SUCCESS, Tests run: 136, Failures: 0, Errors: 0. Concurrent attaches serialize per-note (named lock); concurrent edits with correct versions → exactly one winner via row-level @Version; body cap 50,000 chars validated in-model (MEDIUMTEXT storage); note+revision atomic rollback proven via real DB rejection; link FKs enforced at DB level; bounded /revisions/page endpoint added (summaries without bodies). See CURRENT_TASK and DECISIONS D11.
- Cross-session hazard recorded: concurrent backend test runs from BOTH worktrees share nalok_opencode_test and destroy each other's schema mid-run (observed once, re-run clean). Never run both suites simultaneously.
- N01 (feat/Notes worktree, branch feat/Notes based on 02e53ee): `sh mvnw verify` BUILD SUCCESS, Tests run: 130, Failures: 0, Errors: 0, Skipped: 0. N01a: notes module `com.shaktee.nalok.notes` (independent Markdown notes, NoteKind taxonomy, reversible archive, immutable NoteRevision per content-changing save, expected-version edits rejected before mutation, bounded paginated lists, rollback proven). N01b: FK link tables note_project/section/session_links with idempotent attach, unlink-safe behavior, project-wide retrieval including section links. N01c: /api/notes REST contract (CRUD + revisions + archive/restore + associations + context retrieval) with MockMvc integration tests. See CURRENT_TASK and DECISIONS D10.
- Creation is a single INSERT at version 0: a fresh note must not arrive at the UI pre-bumped; proven by the API round-trip test (PUT with expectedVersion 0).
- Fresh `./mvnw verify`: BUILD SUCCESS, Tests run: 67, Failures: 0, Errors: 0, Skipped: 0 (no leftover intentional-failure reports).
- `git diff --check`: clean.
- Test reports confirm all Spring contexts connect to nalok_opencode_test only.
- Guard ordering proven: a forced misconfigured run (`-Dspring.datasource.url=.../nalok`) failed in 0.3s at environment-postprocessing, before Hibernate, leaving the personal database untouched (no tables created).
- Guard release proven cross-connection: after commit and after rollback, IS_USED_LOCK on a separate physical connection returns NULL (reentrancy-safe check).
- Optimistic locking: stale detached updates rejected on both Project and StudySession (@Version); first write wins.
- History pagination: newest-first with stable id tie-break, disjoint pages, full coverage reachable, size capped at 100, status filtering works.

## Study progress (feat/Study)
- S01 EntityReference embeddable mapped (context_type/context_id), both-or-neither enforced; MySQL round trips for absent, project and section contexts.
- S02 StudySession lifecycle with explicit-Instant transitions, correct elapsed math, @Version optimistic locking, auto-pause of previous ACTIVE session (D06), recoverInterrupted (D07), paused_at chronology checks, named-lock guard for concurrent starts (D08).
- Known limitations: ACIVE enum spelling retained; recoverInterrupted counts crash gap as active time; test guard permits only nalok_opencode_test (by design).

## Next task
F05 (Study creation + polish): create planned study sessions from the Study page, fresh-without-reload summaries, replace the two documented fetch/anchor-effect patterns with a query library, decide Playwright; commit/push strategy for feat/Study to be confirmed with the user. The F04 slice itself is functionally complete after the review-correction pass.
