# Current task

ID: N02 follow-up (review-correction pass) — COMPLETE pending user review
State: feat/Notes worktree `/Users/satish/.local/share/opencode/worktree/notes`; backend 140/0 green; next: R01a Saved Sources backend (web-URL-only first slice recommended)
Goal: Close the four audit gaps from the reported-complete N02: exact race-outcome proof, bounded context retrieval, projection-based revision pages, and lock-release probes.

## What this pass changed
1. Race test rebuilt: both editors reload their note INSIDE the raced section, and the test asserts the exact outcome set — exactly one winner (Note) and exactly one `ObjectOptimisticLockingFailureException` (409); any other outcome fails. The stricter test exposed a genuine InnoDB deadlock (inserts flush before updates → opposite lock order on note row vs revision index). Fix (D12): `updateNote` takes the note row lock first (`NoteRepository.findByIdForUpdate`, SELECT ... FOR UPDATE), so the loser waits there and fails the version predicate cleanly.
2. Context retrieval bounded DB-side: `findNotesForProject` (direct + section links, deduped via `in` subqueries), `findNotesForSection`, `findNotesForSession` (all Note-rooted so updatedAt sorting resolves); service page methods with the shared 100 cap; new endpoints `GET /api/notes/for-project/{id}/page`, `/for-section/{id}/page`, `/for-session/{id}/page` returning summary envelopes (no bodies), 404 on unknown targets. Unpaginated lists remain for small panels.
3. `GET /api/notes/{id}/revisions/page` now selects a closed projection `NoteRevisionSummary` (id, revisionNumber, title, kind, recordedAt) — bodies are never loaded when paging history; full content stays on the per-revision detail path.
4. Note-link guard release proven cross-connection (IS_USED_LOCK on separate physical connections) after BOTH commit and rollback, plus a post-rollback attach-success assertion.

## Verification
- `. dbenv && sh mvnw verify` → BUILD SUCCESS, Tests run: 140, Failures: 0, Errors: 0 (baseline 136).
- `git diff --check` clean. Docs updated (STATUS, DECISIONS D12).
- Attribution correction recorded in STATUS: the missing-table incident is no longer attributed to the frontend session on that evidence alone.

## N01a scope and what was done
1. Package `com.shaktee.nalok.notes` (model/repository/service only — REST is N01c). `Note` (UUID, @Version, title≤200, Markdown body stored raw, NoteKind NOTE/THOUGHT/QUESTION/DECISION/CHECKPOINT, reversible archive flag, createdAt/updatedAt, latestRevisionNumber separate from the JPA version). `NoteRevision` immutable snapshot with FK to the note and unique (note_id, revision_number); revision numbers start at 1.
2. `NoteService` (one class, transaction boundary owner): createNote records the initial revision; updateNote takes an expectedVersion and rejects a mismatch BEFORE any mutation (ObjectOptimisticLockingFailureException → 409 via the existing shared handler, no shared-file edits); content-changing saves record exactly one revision, unchanged saves record none and do not move updatedAt; archive/restore are reversible, not content changes (no revision, but version moves).
3. Bounded list queries: listNotes(kind, archived, page, size) newest-by-updatedAt with id tie-break, page size capped at 100; listRevisions newest first; getRevision scoped to its note.

## Verification evidence (N01a)
- Commands: `sh mvnw verify` (with DB_USERNAME/DB_PASSWORD from the shared dbenv), `git diff --check`.
- Result: BUILD SUCCESS, Tests run: 116, Failures: 0, Errors: 0, Skipped: 0; disposable nalok_opencode_test only (TestDatabaseGuard active).
- Behavior proven: Unicode/multiline body round trip; initial revision on creation; revision 2 recorded with revision 1 preserved unchanged; unchanged save → no revision and updatedAt untouched; kind-only change → revision; stale expectedVersion rejected with content and history intact; oversized (70,000-byte) save rolls back completely — original body kept, revision count unchanged (no orphan revision); archive/restore keep content + history and do not fake a writing change; pagination disjoint/capped/kind+archived filters; revision scoping across notes; unknown note/revision → NotFound.
- Two real bugs found and fixed during verification, both mine, both merge-semantics: `noteRepository.save()` on a transient entity with a preset UUID calls `merge()`, so `revisionRecorded()` was incrementing the discarded original — createNote now continues with the returned managed copy. Same lesson as S03, different disguise; recorded in TESTING.md guidance.

## N02 scope and what was done
1. Body validation moved ahead of the database (D11): Note/NoteRevision bodies are MEDIUMTEXT with an explicit 50,000-character cap enforced in the model — oversized writing is a clean 400 (IllegalArgumentException mapping), never a surprise DB failure. Cap boundary proven (exactly 50,000 saves; 50,001 rejected with no write).
2. Concurrent attaches serialize on a per-note MySQL named lock `nalok:note-links:{noteId}` (same GET_LOCK/afterCompletion-release pattern as the study guard): two simultaneous attaches of the same pair both succeed, exactly one row, original linkedAt. Barrier-synchronized test proves it.
3. Concurrent WRITING proven safe without new code: two threads load the same version and edit together — the service-level expectedVersion check passes for both, and the row-level @Version closes the race (exactly one winner, one new revision, loser's content in no revision). This is the load-bearing finding: the service check alone is NOT sufficient; the @Version update guard is what actually protects notes.
4. Bounded retrieval: new endpoint GET /api/notes/{id}/revisions/page?page&size → PageEnvelope<NoteRevisionSummaryDto> (summaries WITHOUT bodies, newest first, capped at 100); the unpaginated GET /{id}/revisions (full trail with bodies) is unchanged for the editor. FRONTEND CONTRACT ADDITION: prefer /revisions/page for history lists; full list only when the complete trail is genuinely needed.
5. Integrity proofs: note+revision atomicity via a real DB-level rejection (duplicate revision number) — note insert rolls back with it, no orphan row; link-table FK enforcement proven by raw insert with a bogus note_id (DB refuses, not just the service); failed attaches leave zero rows in all three link tables.

## Verification evidence (N02)
- Commands: `sh mvnw verify` (dbenv loaded), `git diff --check` at commit.
- Result: BUILD SUCCESS, Tests run: 136, Failures: 0, Errors: 0, Skipped: 0.
- Environment note: one intermediate run showed 5 errors (`Table 'nalok_opencode_test.projects' doesn't exist`) — caused by the frontend session running its Maven suite concurrently against the SAME disposable DB (its create-drop dropped the schema mid-run). Re-run with no concurrent backend suite → 136/0. Both sessions must not run backend tests simultaneously.

## Next task
ID: R01a
Goal: Saved Sources backend — website/article URL identity (title, URL, created/updated, version-checked edits, reversible archive), note↔source association, bounded REST lists. URLs only: no fetching, scraping or downloads in this slice.


## N01b scope and what was done
1. Three link entities with real FKs and unique (note_id, target_id): note_project_links, note_section_links, note_session_links; linkedAt records association time, never authorship.
2. `NoteLinkService`: attach is idempotent (single row, original linkedAt), unlink removes only the link row (unknown-target no-op), unknown note/target → NotFound; section-linked notes reachable project-wide via section→project join query, deduped against direct links; cross-module existence checks use Projects/Study services.
3. No hard delete anywhere; target deletion has no existing path and FKs would block it (RESTRICT) if ever added.

## N01c scope and what was done
1. `NotesController` /api/notes, thin over services; DTOs only (NoteDto/NoteSummaryDto/NoteRevisionDto/NoteLinksDto); version exposed so clients send expectedVersion.
2. Contract: POST /api/notes (201), GET /api/notes (kind/archived/page/size → PageEnvelope, summaries without body), GET/PUT /api/notes/{id} (PUT requires expectedVersion; mismatch → 409 Concurrent update), POST /api/notes/{id}/archive|restore, GET /api/notes/{id}/revisions[/{rev}], GET /api/notes/{id}/links, POST/DELETE /api/notes/{id}/links/{projects|sections|sessions}/{targetId} (attach 200 with updated links, idempotent; unlink 204), GET /api/notes/for-project|for-section|for-session/{id} (summaries; project-wide includes section-linked; unknown target 404).
3. Error conventions reused unchanged (404 Resource not found / 400 Invalid request / 409): no shared-file edits.

## N01c verification
- NotesApiIntegrationTest (MockMvc, 6 tests): create→get→PUT round trip with Unicode body; revision history via API (2 revisions, oldest preserved); stale expectedVersion → 409 + server content intact; blank title/missing expectedVersion → 400; unknown id/revision → 404; list summaries exclude body + kind/archived filters; archive→filter→restore; link attach/duplicate/unlink + for-project/section/session retrieval + unknown-target 404s.


## Review findings → fixes (all applied)
1. Home treated `204` (no current session) as "still loading": `CardBody` conflated `data === null` with loading. Fixed: loading is decided by the flag alone; nullable `null` is rendered by the card (honest "Nothing running" state).
2. Study lifecycle unreachable after pause: the workspace only mirrored the ACTIVE session. Fixed: PLANNED/PAUSED sessions keep Start/Resume/Finish controls in the history rows; the focused card handles the ACTIVE case. Resume/Start produce a D06 auto-pause server-side; the UI refreshes in place.
3. Timer was skew-sensitive: elapsed was computed as `Date.now() − serverNow`, so a device clock ahead by minutes injected phantom minutes instantly. Fixed: `useLiveStudySeconds` anchors `performance.now()` when the snapshot arrives and adds pure monotonic deltas only (`elapsedSinceAnchor`, pinned by unit tests); re-anchors on every fresh snapshot (key = id + total + serverNow), never wall-clock arithmetic. 1 Hz ticking comes from an interval-callback state, never synchronous setState.
4. TypeScript API mirrors drifted from the backend: `SECTION` → `PROJECT_SECTION`; Projects gained `PLANNED` (filter chip + status styles added); nonexistent Study status `INTERRUPTED` removed (recovery pauses, it does not persist as a distinct status).
5. Coverage hardening (backend): a day whose end EQUALS the coverage instant has provably zero covered time → covered now requires `coverageStart.isBefore(dayEnd)` (strict), closing the fabricated-0:00 boundary. Verified by a dedicated boundary test.
6. Rollout bridge (backend): sessions already ACTIVE before interval tracking lost their ongoing leg at the first interval-maintaining transition. Now the first transition records a bridging interval [activeSince, now] for each D06 auto-paused pre-rollout session — persisted state, not fabricated values — exactly once (marker-gated). Verified by a dedicated fresh-context test.

## Frontend test/verification baseline (this pass)
- `npm run build` (tsc -b + vite): clean.
- `vitest run`: 10 tests, 2 files — duration formatting (incl. 1h59m59s flooring) and anchor math (skew immunity, zero-growth clamp, monotonicity, flooring).
- `npm run lint` (oxlint): 2 documented warnings — the intentional fetch/anchor-in-effect patterns with alive/anchor guards; a query/state library replaces them in F05.
- Backend regression: `./mvnw test` → Tests run: 97, Failures: 0 (was 95; +1 bridge test, +1 boundary-coverage test in their own fresh-context classes to avoid the shared-DB pollution class of bugs).
- Browser-path check with real backend was redone earlier (204 → empty state, summary buckets) — interaction paths (Start/Resume clicks) still have no automated browser tests; that is accepted for this slice and noted below.

## Known limitation (accepted)
- No component/browser-level tests yet; interaction correctness is by contract review for this slice. Browser E2E (Playwright) is an open decision — not silently added.
- `useResource` anchor/fetch effects keep the two documented oxlint warnings.

## Next task
ID: F05 (Study creation + polish)
Goal: Create planned study sessions from the Study page (title/summary/context picker), keep the live timer + summaries fresh without reloads, replace the two documented linter patterns with a query library; decide Playwright yes/no; THEN resolve the commit/push strategy for feat/Study with the user (everything remains uncommitted).
