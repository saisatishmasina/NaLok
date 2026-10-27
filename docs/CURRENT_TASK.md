# Current task

ID: N01a (standalone notes + immutable revisions) — COMPLETE pending user review
State: feat/Notes worktree `/Users/satish/.local/share/opencode/worktree/notes`; backend 116/0 green; commit made; N01b (associations) next
Goal: Independent Markdown notes that preserve every content-changing explicit save.

## N01a scope and what was done
1. Package `com.shaktee.nalok.notes` (model/repository/service only — REST is N01c). `Note` (UUID, @Version, title≤200, Markdown body stored raw, NoteKind NOTE/THOUGHT/QUESTION/DECISION/CHECKPOINT, reversible archive flag, createdAt/updatedAt, latestRevisionNumber separate from the JPA version). `NoteRevision` immutable snapshot with FK to the note and unique (note_id, revision_number); revision numbers start at 1.
2. `NoteService` (one class, transaction boundary owner): createNote records the initial revision; updateNote takes an expectedVersion and rejects a mismatch BEFORE any mutation (ObjectOptimisticLockingFailureException → 409 via the existing shared handler, no shared-file edits); content-changing saves record exactly one revision, unchanged saves record none and do not move updatedAt; archive/restore are reversible, not content changes (no revision, but version moves).
3. Bounded list queries: listNotes(kind, archived, page, size) newest-by-updatedAt with id tie-break, page size capped at 100; listRevisions newest first; getRevision scoped to its note.

## Verification evidence (N01a)
- Commands: `sh mvnw verify` (with DB_USERNAME/DB_PASSWORD from the shared dbenv), `git diff --check`.
- Result: BUILD SUCCESS, Tests run: 116, Failures: 0, Errors: 0, Skipped: 0; disposable nalok_opencode_test only (TestDatabaseGuard active).
- Behavior proven: Unicode/multiline body round trip; initial revision on creation; revision 2 recorded with revision 1 preserved unchanged; unchanged save → no revision and updatedAt untouched; kind-only change → revision; stale expectedVersion rejected with content and history intact; oversized (70,000-byte) save rolls back completely — original body kept, revision count unchanged (no orphan revision); archive/restore keep content + history and do not fake a writing change; pagination disjoint/capped/kind+archived filters; revision scoping across notes; unknown note/revision → NotFound.
- Two real bugs found and fixed during verification, both mine, both merge-semantics: `noteRepository.save()` on a transient entity with a preset UUID calls `merge()`, so `revisionRecorded()` was incrementing the discarded original — createNote now continues with the returned managed copy. Same lesson as S03, different disguise; recorded in TESTING.md guidance.

## Next task
ID: N01b
Goal: Attach/reuse/unlink associations (notes ↔ projects, sections, study sessions) via explicit FK link tables with unique constraints; unlink never deletes the note; section-linked notes reachable project-wide; no stretching of EntityReference.


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
