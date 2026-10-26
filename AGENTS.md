# NEXUS / NaLok agent instructions

## Start here
This is an existing project, not a greenfield rewrite. Root package reported by the user: com.shaktee.nalok.
Read docs/STATUS.md and docs/CURRENT_TASK.md first. Read docs/ARCHITECTURE.md for design changes and docs/PRODUCT.md for feature scope. Read only other files relevant to the task.
Inspect the real repository, pom.xml, Git status and existing instructions before editing. Preserve user changes. The initial status is reported, not verified.
Do not invent files, installed versions, successful tests, or implemented features.

## Design
Keep one Spring Boot application, organized by feature. Projects, Study and Notes work independently.
Use models for valid state/behavior, repositories for persistence, services for complete operations and transaction boundaries.
Avoid automatic Service/ServiceImpl pairs, pass-through abstraction chains, giant unrelated services, and framework-independent layers without an actual need.
A simple public operation may hide substantial implementation. Line count alone is not a quality rule.
Study can be standalone. EntityReference is a shared optional context value; a present reference requires type AND ID. See architecture for integrity limitations.
Preserve valid domain behavior; no class-wide setters allowing arbitrary IDs/status/timestamps.
No Redis, microservices, new backend language or AI agent infrastructure without a concrete task requiring it.

## Scope and execution
Implement only the active task. Future roadmap items are context, not permission to implement all of them.
Keep changes small enough to explain and review. Resolve routine implementation details from existing code.
If a material requirement is unclear, explain the decision needed; do not silently change the product.
Do not delegate to additional agents unless requested.
Do not commit, push, publish, install major new infrastructure or make destructive database changes unless requested.
Never expose credentials, read secret files unnecessarily, or use real bank/email/trading accounts for tests.

## Verification
Discover actual project commands; prefer existing Maven wrapper and test setup.
Typical candidates: ./mvnw compile, ./mvnw test, ./mvnw verify. These are not confirmed until repository inspection.
Use meaningful behavior tests for lifecycle, timing, persistence and reference integrity. No getter/setter-only tests.
Use disposable test data/database; never reset a personal database.
Report exact commands and outcomes. "Not run" is not a pass. Compilation is not persistence verification.
Inspect the diff for unintended changes.
Update STATUS and CURRENT_TASK after actual progress; record significant design decisions in DECISIONS.

## Response
Report outcome, changed files, verification, limitations and the next small step.
Explain one important design choice in plain language so the user learns while building.
