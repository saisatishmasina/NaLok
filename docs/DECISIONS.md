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

## Open decisions
- Supported Java/Hibernate mapping for EntityReference.
- Initial note association cardinality and deletion policy.
- Whether to allow multiple running sessions.
- Interrupted session recovery and duration correction.
- Desktop production database/packaging and backup path.
- Initial supported reader/video sources.

For new decisions record date, context, options, chosen approach, consequences and tests.
Do not turn a suggestion into an accepted decision without task/user evidence.
