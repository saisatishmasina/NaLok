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

## Open decisions
- EntityReference target deletion policy (currently: no FK; existence validated at creation; dangling references possible after target deletion — documented limitation until a policy task).
- Manual correction of recorded study duration.
- Initial note association cardinality and deletion policy.
- Desktop production database/packaging and backup path.
- Initial supported reader/video sources.

For new decisions record date, context, options, chosen approach, consequences and tests.
Do not turn a suggestion into an accepted decision without task/user evidence.
