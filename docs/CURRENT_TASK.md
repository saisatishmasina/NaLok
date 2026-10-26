# Current task

ID: F04 (Frontend scaffold + Home dashboard) — review-correction pass
State: COMPLETE
Goal: Fix the six defects found when re-checking the originally-reported "complete" F04, then verify the fixes; no fabricated data anywhere.

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
