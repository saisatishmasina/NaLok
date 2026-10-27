# Design decisions

## D01 — One modular Spring Boot application
Accepted. No microservices/Redis/Python/Go needed for current scope.

## D02 — Study and Notes independent from Projects
Accepted. Standalone study and notes are valid.

## D03 — Shared optional EntityReference
Accepted concept: type plus ID, composition, complete when present.
Mapping not verified. No promise of polymorphic FK enforcement.
Existence/deletion/concurrency policy must be resolved during S01.

## D04 — Full study workspace in first public release
Accepted: browser, supported readers, notes/backlinks, recall, pegs and graph.
CRUD-only is an internal milestone.

## D05 — Desktop UI
Planned Electron plus React/TypeScript; current repository implementation unknown.

## D06 — Single focused study session
Accepted 2026-10-26. One ACTIVE session globally. Starting or resuming a session
while another is ACTIVE auto-pauses the previous one; its open active leg is
closed with correct elapsed accounting before the new session becomes ACTIVE.
Consequence: no concurrent time overlap; reports can assume non-overlapping legs.

## D07 — Interrupted session recovery
Accepted 2026-10-26. Transitions are persisted immediately, so a session left
ACTIVE after a crash stays ACTIVE with its open active_since. An explicit
recoverInterrupted operation closes the leg at recovery time and pauses.
Limitation: time until recovery counts as active work; manual duration
correction remains an open decision.

## D08 — Single-active enforcement via named lock
Accepted 2026-10-26. Concurrent start/resume calls serialize on MySQL
GET_LOCK('nalok:active-session'), released after commit or rollback through a
transaction synchronization (releasing before commit would reopen the race).
Valid for the single-instance desktop app; revisit for multi-user server use.

## D09 — Persisted study work intervals for period reporting
Accepted 2026-10-26. Session.activeWorkTime totals cannot answer "how much
active work happened this week" for sessions spanning week boundaries, so
every active leg is also persisted as a studyworkintervals row (session_id,
started_at, ended_at; null while ACTIVE). The interval updates run in the same
transaction as the session transition, including D06 auto-pause and D07
recoverInterrupted, and interval timestamps share the transition's instant.
Open intervals are capped at min(now, windowEnd) in period sums; pauses are
excluded by construction. Limitation: sessions started before this change have
no historical breakdown — reports expose an interval-coverage start and the
UI must not fabricate history for older sessions.

Hardened same day (2026-10-26, hardening pass):
- A persisted rollout marker (studyreportingcoverage singleton) records the
  first transitional instant that opened a leg. Coverage start resolves to
  min(marker, earliest persisted interval): intervals recorded before the
  marker are real, reportable data; the marker floors coverage against later
  interval deletion (coverage never reports before the marker even if old
  intervals are deleted).
- Days entirely before coverage start report zero seconds AND covered=false;
  future days of the current week are never covered. A day whose END equals
  the coverage instant is also uncovered (strictly-before rule): it has
  provably zero covered time. The UI presents these as "no reliable data",
  not as a fabricated 0:00.
- Rollout bridge: the first interval-maintaining transition after rollout
  records every pre-rollout ACTIVE session's ongoing leg as a real interval
  [activeSince, now] in the same transaction (marker-gated, exactly once) —
  persisted session state, not fabricated values.
- StudySessionDto carries serverNow next to live activeSecondsTotal so the
  frontend anchors the running timer to a server instant instead of drifting:
  the client adds only monotonic deltas from the snapshot-arrival moment
  (performance.now), never wall-clock arithmetic against serverNow.
- Dangling study context targets answer HTTP 404 ("Context target not found"),
  not 400.

## D10 — Frontend palette: purple family with role-based accents (2026-10-26)
- Context: user replaced the green/yellow/orange/red brand palette with #440A67, #93329E, #B4AEE8, #FFE3FE and asked for the nav to blend into the page corner with the NaLok brand top-left.
- Chosen: root tokens `--color-deep (#440a67), --color-violet (#93329e), --color-lavender (#b4aee8), --color-blush (#ffe3fe)` with `-soft` translucent tints and a darker canvas (#180426). Roles are NOT one-to-one with the old palette: violet carries fills/glows/card accents; blush is the text/icon accent on soft-violet surfaces because #93329E fails contrast on the dark canvas (all nine violet-as-text spots moved to blush); deep shades the canvas and the sidebar tint. Card accent classes renamed `accent-violet/lavender/blush/deep` with the TS type following.
- Consequences: status-dot "active/planned" glow is violet-styled; form selects carry an explicit dark option background. If a future theme engine arrives, these roles form the token grid.
- Sidebar layout decision recorded with the palette: full-bleed grid (264px rail, gap 0), deep→transparent gradient + right hairline instead of a floating glass card — the rail shares the corner ambient glow with the page.

## D11 — NaLok identity: a world you can return to (2026-10-26)
- Context: user described NaLok as a personal world where thoughts become organized, align with goals, and remain available to resume work; asked to apply the proposed logo.
- Chosen: an open lavender world-circle, a rounded blush lowercase n/path, and a warm #F2D6A2 saved-place point on #180426. The point is a logo-only accent, not a new UI status color. No globe grid, chart, or checklist is needed to communicate the whole product literally.
- One SVG in `frontend/public/nalok.svg` supplies both sidebar branding and the favicon so they cannot drift independently. The sidebar keeps the visible mark at 36px and preserves an accessible name in its narrow icon-only layout.
- Verification: frontend build and existing 10 tests passed; XML parsing and built-asset/reference assertions passed. Browser visual review, especially tiny favicon legibility, is still outstanding.

## D12 — Concurrent write protection for Projects (2026-10-26)
- Context: review found stale browser forms could overwrite newer edits (sections had no `@Version` at all; edit requests carried no snapshot proof), and parallel section creation could pick the same position.
- Chosen: every Project and ProjectSection row carries a JPA `@Version`; edit requests (PUT) must send the version from the user's loaded snapshot, services compare before writing, and any mismatch — explicit or Hibernate-detected — maps to 409 "The record changed concurrently; reload and try again". Section appends serialize per project via a pessimistic row lock on the owning Project plus a locking section read, so `max(position)+1` is computed against the latest committed rows (correct even under REPEATABLE READ).
- Consequences: users see an explicit conflict they can retry after the page re-syncs, instead of silent data loss; retries after 409 are safe because edit paths never mix lifecycle changes. The further-stale risk inside the same request is closed by Hibernate's versioned UPDATE. This is per-write optimistic control, NOT a UI merge tool — multi-window simultaneous edits still require the second editor to reload.
- Verification: `ProjectOptimisticLockingTest` (ORM staleness), new integration tests pin the API contract (stale replay 409 without overwriting; current version 200) and 10 parallel appends producing exactly positions 0..9; proxied no-write check proved a stale PUT leaves the personal row untouched.

## D13 — Study-context handoff via URL, consumed once (2026-10-26)
- Context: F08 wants "Study this" on a project and on each section; the target page must plan sessions with the context preselected without inventing a backend handshake.
- Chosen: ProjectDetail renders plain links carrying the context (`/study?context=PROJECT&project=<id>` and `/study?context=PROJECT_SECTION&project=<pid>&section=<sid>`); StudyPage parses the params ONCE (`readPreselection` in `createContext.ts`) into the picker's initial state and immediately strips them (`setSearchParams({}, {replace:true})`). The URL is a one-shot REQUEST for a preselection, not a source of truth: nothing ever re-reads it, so a manual change, a submit, or a later refresh cannot resurrect stale targets either.
- Explicit-choice rule preserved: a preselected KIND is never downgraded on failure. If the parser finds no project id, or the picker's loaded list does not contain the linked project/section (deleted, archived, beyond the bounded 500, or a failed load), the panel keeps kind and ids and shows an explanatory link-note ("Context target not found" is what a dangling reference yields on submit, verified 404 through the proxy). A parse that is not an explicit PROJECT/PROJECT_SECTION handoff yields NO preselection at all.
- Consequences: no backend change and no new dependency (query libraries and Playwright remain open decisions); deep links remain stable (the URL is still valid on a fresh load because the panel re-seeds from params — only post-consumption refreshes land on a clean panel). If a native "start studying" action ever needs richer payload (e.g. pinned position), this handoff is the seam to extend.
- Tests: `Study.preselection.test.ts` pins strict parsing (project/section handoffs, missing section id stays '', unknown kinds → null); the panel behavior (seed, strip, note-on-missing, note cleared on manual change/create) is exercised through build/lint and manual code paths, cite the fake-React harness limitation.

## D14 — Section workbench (2026-10-27)
- Context: user rejected the F08 handoff flow ("dive into section should open its own page and let me start a study session there itself); chose, from provided options: full section page with edit + study card + sibling navigation, "plan now, start manually", timer on the section page.
- Chosen: a dedicated workbench page per section (route `/projects/:projectId/sections/:sectionId`, key-remounted per section) hosting hero + edit, a locked-context plan card (creates a PLANNED session with `PROJECT_SECTION` context; Start stays explicit — the one-ACTIVE-session auto-pause must never happen from a click the user didn't intend), the section's own sessions list and the globally-focused timer card. Sibling sections and a prev/next stepper are plain links over the already-loaded list (hopping costs no extra request). To make the list honest across visits, a small READ-ONLY backend endpoint was added: `GET /api/study/sessions/by-context` (both reference parts required — 400 otherwise; unknown reference → empty envelope) — filtering global history client-side would have hidden rows behind a fixed page count.
- Consequences: reuse over new abstraction — `HistoryRow`/`FocusedSessionCard` are exported from Study.tsx rather than duplicated; the per-section draft/edit code follows the same captured-draft contract as ProjectDetail (verified pattern, re-reviewed rather than re-tested via the fake-React harness — a follow-up can extract it). The section-level "Study this" handoff link was replaced by "Open"; the PROJECT-level link and URL preselection (D13) remain, since they hand OFF to /study without leaving the workbench context behind.
- Tests: `sessionsCanBeListedByContextAndIncompletePairsAreRejected` (membership, ghost-empty envelope, incomplete pair 400); frontend build/tests/lint; proxied no-write checks only (the workbench planning path itself needs a browser session, so creating sessions through the UI remains user-review territory).

## Open decisions
- EntityReference target deletion policy (currently: no FK; existence validated at creation; dangling references possible after target deletion — documented limitation until a policy task).
- Manual correction of recorded study duration.
- Initial note association cardinality and deletion policy.
- Desktop production database/packaging and backup path.
- Initial supported reader/video sources.

For new decisions record date, context, options, chosen approach, consequences and tests.
Do not turn a suggestion into an accepted decision without task/user evidence.
