# Current task

ID: Phase-1 reliability fixes (post-F08.1 audit) — COMPLETE pending user visual review
State: All four audited workbench/workflow defects fixed; frontend **35/35** (26 prior + 9 new regression tests), clean build, lint exactly the 1 documented warning, proxied no-write checks passed (by-context page/size honored, bad params 400); backend untouched this phase (109/109 from F08.1 stands). Commit/push on user request.
Goal: Section workbench and Study hub must survive refreshes, lifecycle actions and section hops without destroying open drafts, reachability of old sessions, or duplicated requests. Then: user visual review → commit/push checkpoint on feat/Frontend → branch feat/Frontend-Notes → integrate origin/feat/Notes 0f3e738 → Notes frontend in 3 increments (N01d handbook).

## F05 scope and what was done
1. Palette swapped to the user-chosen purple family. Role mapping (DECISIONS D10): violet #93329E = fills/glows/card accents, lavender #B4AEE8 = secondary, blush #FFE3FE = bright text/icon accents on soft backgrounds (violet itself is too dark for text on the dark canvas), deep #440A67 = canvas shade. Tokens renamed (`--color-deep/violet/lavender/blush` + `-soft` tints), canvas darkened to #180426, card accents renamed `accent-violet/lavender/blush/deep` (CSS + `CardAccent` + Home usages), all nine violet-as-text spots moved to blush, ambient glide stays violet/blush.
2. Sidebar now blends into the page: full-bleed shell grid (`264px 1fr`, gap 0, no page padding), sidebar is an edge-to-edge rail with a deep→transparent gradient + right hairline (no floating card), brand block anchored top-left ABOVE the Spaces group, content gets real padding. Narrow layout kept (76px rail).
3. `index.html` title → "NaLok".
4. Study creation from the UI: `createStudySession` API helper (payload mirrors `CreateStudySessionRequest`); `CreateSessionPanel` — title (max 200), optional summary, optional context picker (No context / Project… / Section…) with a DEPENDENT select: section loads via `/api/projects/{id}/sections` only after a project is chosen; picker data loads lazily on first open. POST → refresh key bump → fresh history/focused panel without page reload. Errors surface from ProblemDetail ("Title is required" 400, dangling target 404). State resets live in change handlers; effects only fetch.
5. Fresh-without-reload: refresh-key refetch (existing `useResource`) covers timer, focused panel, history — used by creation and lifecycle actions.
6. User-approved NaLok logo applied: `frontend/public/nalok.svg` is shared by the browser favicon and sidebar. The open world contains a rounded lowercase n/path and a warm saved-place point (D11). Sidebar mark is 36px, with a semibold wordmark; narrow layout centers the mark and retains an accessible NaLok name. Removed the unused Vite favicon; preserved existing F05 edits.

## Fresh-without-reload (F05 scope)
- `useResource` grew an optional `pollMs`: the same effect re-runs the loader on an interval (no new fetch-in-effect sites) — Home polls current-session 10s, projects/weekly 30s; Study polls current+history 10s. A failed poll KEEPS the previous real data (error note only) instead of blanking a card; `loading` never flashes between polls.
- The query-library option stays an open decision; polling is the dependency-free fulfillment of the agreed freshness scope.

## Audit fixes (post-implementation review, same day)
1. Section context was unusable — the project select only rendered in Project mode. Now the project select appears for BOTH context modes; sections load only under Section.
2. Incomplete context could silently create a STANDALONE session (submission fell through when target empty). Now context is an explicit choice: missing project/section is a validation error telling the user to pick a target or switch back to "No context".
3. Create did not reset pagination — a new session (newest-first) could appear on a page the user wasn't on. Now creation resets to page 1 and refreshes.
4. Empty-state wording was stale ("create one in a later slice") — now "create one above".

## Verification (exact)
- `npm run build` (tsc -b + vite): clean.
- `vitest run`: 10/10 (formatting + anchor math).
- `npm run lint` (oxlint): now **1** warning — only the documented anchor-in-effect in `useResource.ts`. The poll rewrite folded fetching into the same effect and the spread dep list satisfies exhaustive-deps, so the second documented warning disappeared; anchor-in-effect remains inherent.
- Through the Vite proxy against the dev backend: POST `{}` → 400 ProblemDetail "Title is required" (contract verified; nothing persisted to the personal DB), GET `/api/projects?size=100` → 200 for the picker, GET current → 204, summary → 200. HMR confirmed applying Study.tsx/index.css updates live.
- Backend untouched by this slice (97/0 from the F04 pass stands).

### Logo application verification (2026-10-26)
- `npm run build`: passed; `npm test`: 10/10 passed.
- `npm run lint`: completed with one existing `react(set-state-in-effect)` warning in `src/lib/useResource.ts:126`; no logo warnings.
- Python standard-library XML/build-output assertions: SVG parses, built asset matches source, built HTML references `/nalok.svg`, and the old favicon is absent from the build.
- `git diff --check`: clean. No browser visual verification or backend tests run for this logo-only change; visual review remains next.

## Open decisions (user)
- Playwright browser tests: yes or no.
- Query-library replacement of the documented effect pattern: kept `useResource` + refresh key (adds no dependency); a state/query library is optional later if the fetch patterns multiply.

## F07 (lifecycle + sections) — what was done
- Backend: controller `POST /api/projects/{id}/start|pause|complete|archive` — each follows the model's own state rules and rule violations surface as 409 ProblemDetail; `POST /{id}/sections` → 201 via `ProjectSectionService.appendSection` (APPENDS after the last position — callers never type positions) and `PUT /sections/{sectionId}` → 200 via `updateSection` (one atomic edit intent). Validated `CreateSectionRequest`/`UpdateSectionRequest`.
- Frontend: `ProjectDetail.tsx` rewritten — visible buttons DERIVED from persisted status (`AVAILABLE_ACTIONS`: PLANNED→start/archive, ACTIVE→pause/complete/archive, PAUSED→start/archive, COMPLETED→archive, ARCHIVED→none); archive is terminal + two-step (first click arms a danger-styled "Confirm archive" with a cancel chip); add-section form + per-row Edit; every form's Cancel is disabled while its write is pending (F06 audit rule carried here).
- F06 review defects closed in the same pass: successful creates force an explicit `list.refetch()` (visible refresh even when filter/page are unchanged), the INITIAL load participates in `useResource`'s no-overlap tracking, and `list.refetch` is destructured so exhaustive-deps passes cleanly. New CSS classes in `index.css`: `.lifecycle-row`, `.action-btn.danger`, `.sections-head`, `.section-add-form`, `.section-main/.section-title/.section-desc`, `.section-item.editing`.

### F07 verification (exact)
- `./mvnw test` (env via normalized .env parse): **Tests run: 106, Failures: 0, BUILD SUCCESS** — 3 new API tests (full lifecycle incl. double-complete/double-archive 409; appended sections listed position-ordered with DB readback; atomic section edit + blank-title 400 + ghost 404). One intermediate compile failure (`Integer secondId` → `String`) fixed.
- Proxied no-write contract checks (:5173, random UUIDs — real project untouched): POST `{}` sections → 400 "Section title is required"; POST lifecycle random id → 404; PUT random section id → 404; GETs → 200 with real data only.
- `npm run build`: clean. `npm test`: **16/16**. `npm run lint`: back to exactly the 1 documented anchor-in-effect warning (`useResource.ts:152`).
- `git diff --check`: clean. Personal `nalok` DB: no writes (read-only checks only).

## F07.1 (review fixes) — what was done
1. Stale-edit protection: `ProjectSection` now has `@Version`; edits must carry the snapshot's `version` (DTOs expose it, PUT requests validate it as `@NotNull`, services compare before writing) and a mismatch is a 409 "The record changed concurrently; reload and try again" (handler widened to the parent `OptimisticLockingFailureException` so ORM-detected staleness is covered by the same mapping).
2. Concurrent section appends: `appendSection` locks the project row pessimistically and reads the sections through a locking query — two racing creates serialize on the project row and cannot compute the same next position.
3. Refresh no longer destroys state: `useResource` distinguishes a refresh of the SAME resource (manual refetch / poll restart — keeps the last good data, so open forms survive and a failed refresh retains data under its note) from a NEW resource (deps change — resets to loading). The project/section edit forms resync from the server after failed saves.
4. No identical-request overlap, even across effect restarts: a hook-level busy counter gates polls, and a refetch clicked while busy is deferred once until the in-flight load settles.

### F07.1 verification (exact)
- `./mvnw test` (env via normalized .env parse): **Tests run: 108, Failures: 0, BUILD SUCCESS** — 3 new tests: project stale-version replay → 409 + current version → 200 with the row never overwritten; the same contract for section edits; 10 parallel service-level appends → positions exactly 0..9 each taken once. (`nalok_opencode_test` only; intermediate compile/import errors and one JsonPath Integer-cast fixed.)
- Proxied no-write checks (:5173): PUT real project, stale version 99999 → 409 and GET confirms the personal row untouched; missing version → 400 "Project version is required"; ghost project/section PUT → 404; sections POST `{}` → 400; GETs → 200.
- `npm run build`: clean. `npm test`: **16/16**. `npm run lint`: exactly the 1 documented set-state-in-effect warning (`useResource.ts:204`). `git diff --check`: clean.
- Known limitation: refresh/defer semantics are pinned by code paths and proxied checks, not vitest unit tests (component-render testing still awaits the Playwright decision).

## F07.2 (round-2 review fixes) — what was done
1. Draft submits the version CAPTURED at edit-open (`ProjectHeader`/`SectionRow` keep `draftVersion` in state when the editor opens; the refreshed prop version can no longer authorize stale content). When the record changed under an open draft, Save is disabled and an explicit "Discard draft & reload" button re-syncs; reopening the editor always re-seeds fields from the CURRENT snapshot.
2. Per-resource coordination in `useResource`: a refactored hook keeps `{busy, pending, request}` keyed to the resource identity (not the effect closure), so an older generation's draining load can never erase a newer resource's queued refresh; the newest-issued request token wins writes; refetch-while-busy is queued once per resource and runs when busy reaches 0; a pollMs-change restart while a load is in flight reuses the running load rather than issuing a second identical request.
3. Retained data stays visible on a failed refresh: Projects list and ProjectDetail/SectionsCard now show the last good data under a `stale-note` (error-only card only when nothing was ever loaded).

### F07.2 verification (exact)
- New dependency-free regression tests (no Playwright required): `src/test-support/fakeReact.ts` — a minimal React-hook-runtime substitute — runs the REAL `useResource` and REAL `ProjectHeader` (exported for tests) in `useResource.refresh.test.ts` (3 tests: old-gen drain must not erase B's queued refresh with 'B final' winning; pollMs change during a pending load → 1 request; refetch-while-busy → exactly one follow-up) and `ProjectDetail.draft.test.ts` (3 tests: submits captured version 0 despite props refreshing to 1; Save disabled when the record changed under the draft; reopen shows the current snapshot after explicit discard).
- `vitest run`: **26/26** (16 prior + 6 new + 4 new F08 URL-handoff parse tests). `npm run build`: clean. `npm run lint`: exactly the 1 documented warning (`useResource.ts:228`). `git diff --check`: clean.
- Node simulation of the round-2 review scenarios against the real transpiled modules: Save disabled + stale draft submits version 0; reopened editor shows the saved snapshot; A-drain preserves B's queued refresh (B requests 1→2, final data 'B final'); pollMs change → 1 unresolved request. All pass.

## F08 (session creation from a project/section) — what was done
1. Handoff links: ProjectDetail header "Study this" → `/study?context=PROJECT&project=<id>`; per-section "Study this" → `/study?context=PROJECT_SECTION&project=<pid>&section=<sid>` (anchors styled via `a.action-btn`).
2. StudyPage consumes the URL ONCE via `readPreselection` (moved into `createContext.ts`, pure + tested): the panel seeds contextKind/projectId/sectionId from it and the params are immediately stripped (`setSearchParams({}, {replace:true})`) — the URL is a one-shot handoff, never a source of truth, so later changes or refreshes cannot resurrect stale targets (D13).
3. Failure semantics (explicit-choice rule carried over): a link mid-edit that is stale or failed to load NEVER downgrades the context kind — "The linked project/section is not loadable…" surfaces as a link-note while the kind stays preselected; a missing project id in the link is noted too. Manual picker changes and successful creation clear the note.
4. Proxied no-write checks confirm the failure contract the panel relies on: create with a ghost PROJECT context reference → 404 ProblemDetail "Context target not found", no row written.

Next step: user visual review at http://localhost:5173 (study links on project/sections, preselection note path, plus all F07 UI). Commit/push on user request.

## F08.1 (user-ordered flow change) — section workbench, 2026-10-27
- User feedback: handoff links were the wrong flow — a section should have its OWN page, and studying should start there. Chosen (after user choices): open the section page instead of handoff; "plan now, start manually"; the running timer shows on the section page; sibling sections list + prev/next stepper for easy hopping (options 1+3 combined).
- Backend (small read-only addition): `GET /api/study/sessions/by-context?entityType&referenceId` — sessions whose context references one target, newest-first with the same page/size caps as history; incomplete reference pair → 400 (EntityReference both-or-neither at the URL); unknown reference → EMPTY envelope (200, honest "nothing here yet"). Derived repository query; service validates the pair; controller maps ProblemDetail as usual.
- Frontend: new `SectionWorkbench` page at `/projects/:projectId/sections/:sectionId` — hero (title/description/position + in-place edit with the SAME captured-draft-version contract), "Study this section" card (context LOCKED to the section, title prefilled editable, Plan → PLANNED session; note tells the user to press Start below), sessions-for-this-section list (reuse `HistoryRow` + `FocusedSessionCard` exported from Study.tsx) with Start/Pause/Resume/Finish + live poll 10s; prev/next stepper and sibling nav are plain route links over the SAME loaded sections (no refetch on hop). The workbench is keyed by sectionId so a dive always starts from that section's current snapshot. ProjectDetail section rows now say "Open" instead of "Study this"; the PROJECT-level "Study this" handoff link and the /study URL preselection remain untouched.
- Verification (exact): `./mvnw test` → **Tests run: 109, Failures: 0, BUILD SUCCESS** (new API test: real project+section created through the API, two sessions bound PROJECT_SECTION + one PROJECT-tagged — by-context returns exactly the two, membership asserted (not order — same createdAt instant possible); ghost reference → empty envelope; missing referenceId → 400). One test bug fixed en route: a greedy `"id"` capture regex inside the helper swallowed `projectId` from section JSON; a second: the helper hardcoded PROJECT_SECTION and then wrongly asserted 201 on the by-design 404. Frontend `vitest run` → **26/26**; `npm run build` clean; oxlint exactly the **1 documented** warning. Proxied no-write checks (:5173): by-context ghost id → 200 empty; missing referenceId → 400; bad entityType → 400; a real section id returned the user's own real PAUSED session (read-only); GETs 200 only. Personal `nalok` DB untouched.

## Phase-1 reliability fixes (post-F08.1 in-memory audit), 2026-10-27
An in-memory simulation against the real transpiled modules reproduced four audited defects; all are fixed and pinned by new dependency-free regression tests.

1. **Refresh-identity bug (SectionWorkbench + StudyPage)** — a shared `refreshKey` bumped by Plan/Start/Pause/Finish/Refresh was part of every `useResource` deps list, so ANY action recreated EVERY resource identity: data cleared, the hero (with any open edit draft) unmounted mid-action, and a failed refresh blanked the page. It also surfaced that an identity change with an identical unresolved GET in flight issued a second duplicate GET.
   Fix: resources keep STABLE identities (sections `[projectId]`; section sessions `[sectionId, page]`; Study hub `[]` / `[historyPage]`). Plan/edit/lifecycle/Refresh actions now run coordinated `refetch()` calls on the two session resources (and the section editor refetches sections) — the failure paths already built into `useResource` (retain data, queue-while-busy, stale-note) now actually engage. `StudyCard`'s and `SectionHero`'s guards report per-section via the section id (a confirmed hop naturally deactivates its own stale guard).
2. **Section sessions unreachable past 20** — `fetchStudySessionsByContext` always sent page 0/size 20 and the card had no pager. Fix: page/size params in the helper + the same Back/Page/Next pager as Study history; `onPage` lives in the Workbench so a hop resets it. Older PLANNED/PAUSED rows keep reachable Start/Resume controls.
3. **Whole-workbench remount refreshed the project on every hop** — `key={sectionId}` on the entire Workbench re-fetched the project's sections each hop (a superset refetch). Fix: Workbench is NOT keyed; only the hero + plan form subtree is keyed per section, so hops reuse the loaded list (stepper/sibling nav stay live), while edit/plan forms still start clean from the section the user landed on. Hopping also used to silently discard an open edit — now links carry a `hop` guard: an unsaved draft arms a `window.confirm` before navigation ("unsaved edits — leave without saving?").
4. **Plan title not prefilled** — the section workbench plan card initialized the title to `''` although the documented flow says it is prefilled from the section and editable. Fix: seeded from `section.title`; Reset fields restores the prefill (never a blank form); typed-but-unplanned content (title ≠ section title or summary ≠ '') reports itself to the hop guard; a successful plan resets to the prefill and clears the guard.

### Phase-1 verification (exact)
- New tests (no Playwright, same fake-React harness): `SectionWorkbench.test.ts` (5: plan refetches sessions without sections reload and hero survives; refetch-while-busy queued once — no overlapping identical GET; failed sessions refresh retains data + section; pager page/size + hop page-reset from the LATEST closure + unsaved-edit confirm declined/accepted with zero sections fetches; plan-title prefill/dirty reporting/plan resets to prefill), `Study.refresh.test.ts` (4: lifecycle action refetches both resources; second refetch queued once; failed history refresh keeps rows under a stale-note; no leaked picker fetches).
- `vitest run` → **35/35** (26 prior + 9 new). `npm run build` (tsc -b + vite): clean. `npm run lint` (oxlint): exactly the **1 documented** warning (`useResource.ts:228`). `git diff --check`: clean.
- Backend untouched this phase; the F08.1 **109/109** run stands.
- Proxied no-write checks (:5173, random UUIDs): by-context `page=0&size=20` → 200, `page=1` → 200 (pagination params honored), missing entityType → 400.
- Known limitation: page state resets only via in-page hop links (guarded) — a browser Back/Forward that changes the section id without the click handler keeps the current list page (one extra "Back" click is needed); the pager itself is always visible.

Next step: user visual review at http://localhost:5173 (sections workbench flows, timer, pager, hop guard). Then (user-authorized) commit+push the Projects/Study checkpoint on feat/Frontend, branch feat/Frontend-Notes, integrate origin/feat/Notes 0f3e738, and deliver Notes in three reviewable increments.