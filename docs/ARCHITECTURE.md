# Architecture baseline

## Stack
Existing: Java, Spring Boot, Maven, JPA/Hibernate, MySQL.
Planned UI: React/TypeScript and Electron. Inspect repository before selecting versions or scaffolding.
One backend application. Frontend calls application operations through a small API.
MySQL is fine for development. Public desktop database packaging is unresolved: choose deliberately before release; do not require ordinary users to install MySQL without an explicit decision.

## Modules and information ownership
Projects owns goals, sections and project lifecycle.
Study owns focus, lifecycle, time accounting and session history.
Notes owns note content and editing independently of sessions.
Workspace owns tabs, panels, selected source and restoration; it is distinct from a timed session.
Sources owns reusable website/video/file identity and source locations.
Recall owns prompts, attempts and scheduling. Pegs owns mnemonic relationships.
Use existing package layout; do not relocate everything to match an ideal diagram.

## Shared references: accepted concept, mapping not verified
world/reference/EntityType and EntityReference represent type + UUID.
Initial supported targets: PROJECT, PROJECT_SECTION. Add targets only with implemented behavior.
An absent whole reference means standalone; a present reference has both fields.
Use composition, not a shared superclass for StudySession and Note.
The earlier Java record example was conceptual, NOT confirmed JPA mapping.
Inspect Java/JPA/Hibernate versions. Choose compatible embeddable mapping or explicit columns with conversion, and prove null/non-null round trips in a persistence test.
No separate entity/table is required just to share a value object.
Polymorphic type+ID has no ordinary FK to multiple target tables. Existence validation, deletion and concurrent deletion integrity must be designed explicitly.
Do not claim a pre-insert existence check alone provides database-level integrity.
Do not introduce a global entity registry without weighing its complexity.
One context per study session is the initial assumption. Notes may need multiple associations; decide cardinality before building association storage.
Linking to a section does not require storing a duplicate project ID; project-level reports must explicitly include section-associated sessions when intended.
A reusable note is not owned by a session: removing an association must not delete the note.

## Lifecycle and time
Proposed session states: PLANNED, RUNNING, PAUSED, FINISHED; confirm against current code and task.
Inject a clock or pass explicit time for deterministic domain tests.
Persist timing transitions, not one timer write per second.
Define finish-from-paused, repeated actions, interrupted session recovery and manual correction explicitly.
Single active session globally is unresolved, not an implicit rule.
Do not conflate elapsed duration, active work time and project age.

## Boundaries
Domain models enforce local invariants; services coordinate transactions and external checks.
Persist important world-history events atomically with local state changes when introduced.
World history is not automatically event sourcing; no broker required.
Expose bounded DTOs at UI boundaries; don't leak lazy entity graphs.
Validate foreign context through an explicit module API/validator; avoid importing every module's internals.
Do not abstract every query before a real use case exists.

## Resource use and trust
Paginate histories, use summary queries, inspect query counts, avoid N+1 patterns.
Bound background work, render documents incrementally, unload inactive browser views.
Remote pages must not access Node, local files, privileged IPC or backend credentials.
Sandbox/isolate remote content, allowlist IPC, validate navigation and permissions.
Protect a local backend against arbitrary remote origins; localhost alone is not authentication.
Keep originals separate from annotations. Preserve provenance and show unresolved anchors.
Debounced autosave needs visible status, error handling and safe close behavior.
