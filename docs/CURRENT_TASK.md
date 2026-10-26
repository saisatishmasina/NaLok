# Current task

ID: A00 → Projects milestone (done)
State: COMPLETE
Goal: Establish actual repository state and complete the Projects backend with verified tests.

## Acceptance — met
- Actual versions, packages and current behavior recorded in STATUS.
- Existing user changes preserved (service files carried into the milestone unchanged except documented fixes).
- Production code changed only for verified defects: Project.start() guard, createProject persistence, archive idempotency guard.
- Test outcomes from real executions; DB access was provisioned by the user after environment blockers.
- Next task (S01/S02 on feat/Study) has explicit scope below.

## Verification evidence
- Commands: `./mvnw compile`, `./mvnw test`, `./mvnw verify`, `git diff --check`.
- Results: BUILD SUCCESS; Tests run: 30, Failures: 0, Errors: 0, Skipped: 0.
- Database: disposable nalok_opencode_test (create-drop), separate from the personal `nalok` database.
- Pushed to origin/feat/Projects.

## Next task
ID: S01+S02 (Study start)
State: READY
Goal: Make StudySession compile and complete standalone session lifecycle with deterministic timing.

## Scope (Study, feat/Study branch)
1. Reference mapping (S01): optional Project/Section context on StudySession (type + UUID, both or neither), embeddable or explicit columns; prove null and non-null round trips in MySQL persistence tests; document deletion/integrity policy.
2. Lifecycle (S02): fix compile blockers; states PLANNED/ACTIVE/PAUSED/COMPLETED; start/pause/resume/finish with correct elapsed-direction math; finish from paused also closes timing; injected Clock for deterministic tests; invalid-transition tests.

## Acceptance
- Unit tests for transitions and timing; MySQL integration tests for persistence and reference round trips.
- Open product decisions (multiple active sessions, interrupted-session recovery) asked before implementing.

## Execution evidence
Not run yet.
