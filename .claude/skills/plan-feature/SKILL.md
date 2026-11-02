---
name: plan-feature
description: Plan the implementation of a feature or change in this Spring Boot + Angular project before any code is written. Produces a reviewed implementation plan covering requirements, API contract, database migrations, backend and frontend changes, tests, risks, and an ordered task list assigned to the backend-engineer, angular-architect and code-reviewer agents. Use this whenever the user asks to plan, design, scope, break down or estimate a feature, asks "how should we implement/build X", wants an approach before coding, or describes a non-trivial new feature (new fields, endpoints, pages, filters, edits, integrations) without saying to start coding right away, even if they don't use the word "plan".
---

# Plan a feature

Your job is to turn a feature request into a plan that someone (usually the backend-engineer and angular-architect agents) can implement without having to make major decisions themselves. A good plan:
- makes the decisions that are expensive to change later (API contract, data model, migrations), and
- leaves small implementation details to the implementer.

**Do not write or change application code while planning.** The output is a plan the user approves first. Code written before the plan is agreed tends to anchor the design and gets thrown away.

## 1. Understand the request

- Restate the feature in 1–2 sentences and write acceptance criteria as observable behavior ("sorting by Department desc shows Z→A, 20 per page").
- Find the ambiguities that change the design:
  - Where does the data come from, and is it stored or derived?
  - Who can see it or change it?
  - Sync or async?
  - Units and formats (monthly vs yearly, currency)?
  - What happens to existing data?

  Ask the user about those, in one batch of at most 3–4 questions, before designing. Include your recommended answer so they can just confirm.
- For minor ambiguities, pick the sensible default and record it under **Assumptions**. Asking about everything wastes the user's time; asking about nothing produces plans built on guesses.

## 2. Study the codebase first

Plans fail when they ignore what's already there, so read before you design:
- **Standards:** read `.claude/agents/backend-engineer.md` and `.claude/agents/angular-architect.md`. The plan must follow them (layering, DTOs, ProblemDetail errors, Swagger on every endpoint, Signals/RxJS, OnPush, tests), so the implementing agents don't have to fight it.
- **Backend:**
  - `backend/pom.xml` and `application*.yml` (profiles, datasource)
  - the feature package (`backend/src/main/java/.../<feature>/`): entity, repository queries, service, controller, DTOs, sort/filter whitelists
  - `db/migration/` for the latest version number
  - `db/seed/{vendor}/` for demo data
  - tests and test fixtures (`src/test/resources/*.sql`)
- **Frontend:** `frontend/src/app/<feature>/`, covering models, service, components, specs and routes.
- **Find the closest existing example** of what's being built (another endpoint, column, form) and plan to follow it.

Verify these facts rather than assuming them, because they change over time.

## 3. Design

Cover each area below that the feature touches. Skip the ones it doesn't, and say "no change" rather than padding.

**API contract (decide this first, since both sides depend on it)**
- Method, path, query params with allowed values, request and response JSON with example payloads, and status codes including errors.
- Call out breaking changes explicitly: renamed fields, removed params, changed meanings. Prefer additive changes unless the user agrees to break the contract.

**Data model & migrations**
- New or changed columns/tables, types, constraints (`NOT NULL`, `CHECK`, `UNIQUE`), and indexes for any new filter or sort column.
- **Never edit a migration that may have been applied to a persistent database.** Add the next `V<n>__...sql`. Editing an existing migration is acceptable only if you've confirmed no persistent DB has run it, and the plan must say how you confirmed that.
- **Existing rows:** for a new `NOT NULL` column, decide the backfill (a default, or computed from other data).
- **Demo seed data:** this project has vendor-specific seed folders (`db/seed/h2/` and `db/seed/postgresql/`). Plan updates to both, plus the test fixture SQL.

**Backend changes** (by layer, following existing patterns)
- Entity, repository/query, service rules, controller, DTOs, validation, error cases.
- Swagger annotations for anything new or changed.
- Watch for N+1 queries, pagination, transaction boundaries, and concurrent updates (`@Version`) on write features.

**Frontend changes**
- Model types, service methods, component state (Signals vs RxJS), template changes, forms and validation.
- Loading, empty and error states, and accessibility (labels, keyboard, `aria-*`).

**Tests**
- Name the specific cases, not "add tests": success, validation failure, not-found or conflict, edge cases (empty, boundaries, ties in sorting), and the frontend interactions.

**Security** (only if relevant): who may access or modify the data; input that reaches queries.

## 4. Alternatives, risks and scope

- **Alternatives:** only when there's a real choice (e.g. "store order count vs compute it"). Give a one-line trade-off each and your recommendation. Don't invent strawmen.
- **Risks:** what could break (existing clients, performance at scale, data migration), each with its mitigation.
- **Out of scope:** what you deliberately are not doing. This prevents scope creep during implementation.

## 5. Break it into tasks

Order the tasks so each one leaves the build green, and assign each to an agent:

1. **Backend first** (`backend-engineer`): migration → entity/query → service → controller + Swagger → tests. The API contract must exist before the UI uses it.
2. **Frontend** (`angular-architect`): model → service → component/template → specs.
3. **Review** (`code-reviewer`): a final independent review of the whole change.
4. **Verify** (`release-verifier`): after review fixes, prove the change works: clean-snapshot build, all tests, a smoke test of the running app and the frontend/backend contract. The feature is done only on a `SHIP` verdict.

Each task needs:
- what to change (files or areas),
- how to verify it: `cd backend && mvn test` / `cd frontend && npx ng test --watch=false --browsers=ChromeHeadless` / `npx ng build`,
- a size: S (< 1h) / M (half day) / L (> half day). An **L** usually means the task should be split.

Scale the plan to the feature. A new sortable column needs half a page; a new edit workflow with permissions needs more. A short plan that's right beats a long plan nobody reads.

## 6. Save, present, and stop

1. Save the plan to `docs/plans/<YYYY-MM-DD>-<feature-slug>.md` using the template below, so it can be reviewed, versioned and handed to the agents.
2. In the chat, give a short summary: the key decisions, open questions, task count, and the file path.
3. **Stop and ask for approval.** Do not start implementing until the user says so. When they approve, the plan's task list is the brief you hand to each agent.

## Plan template

```markdown
# Plan: <Feature name>

**Date:** YYYY-MM-DD · **Status:** Draft — awaiting approval

## Goal
<1–2 sentences: what and why>

## Acceptance criteria
- [ ] <observable behavior>

## Assumptions & open questions
- Assumption: <decision made where the request was unclear>
- Question: <needs the user's answer> (recommended: <answer>)

## API contract
<endpoint, params, example request/response, status codes; breaking changes called out — or "No change">

## Data model & migrations
<columns, constraints, indexes, migration file name, backfill, seed + fixture updates — or "No change">

## Backend changes
<by layer, following existing patterns>

## Frontend changes
<models, service, components, states, accessibility — or "No change">

## Tests
- Backend: <specific cases>
- Frontend: <specific cases>

## Alternatives considered
<only if a real choice existed>

## Risks
- <risk> → <mitigation>

## Out of scope
- <deliberately not doing>

## Tasks
| # | Agent | Task | Verify | Size |
|---|-------|------|--------|------|
| 1 | backend-engineer | ... | `mvn test` | S |
| n-1 | code-reviewer | Review the full change | review verdict | S |
| n | release-verifier | Verify the change is shippable | `SHIP` verdict | S |
```
