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

## D10 — Notes preserve every content-changing explicit save (2026-10-26)
Accepted. A note's writing history is recorded as immutable NoteRevision
snapshots: creation records revision 1; every save that changes title, body
or kind records exactly one more; an unchanged save records nothing and does
not move updatedAt; archive/restore are record-level state changes, never
content changes (no revision, JPA version moves).
- Revisions answer "how did this piece of writing change"; separate
  THOUGHT/DECISION/CHECKPOINT notes answer "how did my understanding
  develop" — no event sourcing or WorldEvent system needed for this.
- Edits carry an expectedVersion; a mismatch is rejected before any
  mutation, preserving the newer server content and the client's draft.
- Association cardinality (multiple FK link tables per target type, no
  owning context, unlink-safe) is accepted in principle for N01b; exact
  link-table design lands with that task.

## Open decisions
- EntityReference target deletion policy (currently: no FK; existence validated at creation; dangling references possible after target deletion — documented limitation until a policy task).
- Manual correction of recorded study duration.
- Initial note association cardinality and deletion policy.
- Desktop production database/packaging and backup path.
- Initial supported reader/video sources.

For new decisions record date, context, options, chosen approach, consequences and tests.
Do not turn a suggestion into an accepted decision without task/user evidence.
