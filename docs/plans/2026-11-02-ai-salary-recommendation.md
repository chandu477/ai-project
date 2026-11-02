# Plan: AI salary recommendation for new users

**Date:** 2026-11-02 · **Status:** Draft — awaiting approval

## Goal
When adding a new user, the admin enters the user's years of experience and gets a monthly salary proposal from Claude. The proposal is grounded in salary statistics from the existing users in the database. The admin can accept or edit it, then save the user.

## Acceptance criteria
- [ ] The listing page has an **Add user** button that opens `/users/new`, a form with Username, Years of experience and Monthly salary.
- [ ] With a valid experience value, **Suggest salary** shows within ~15 s:
  - a recommended monthly salary and a low–high range,
  - a 1–3 sentence rationale,
  - how many existing users it was based on.
- [ ] **Use this salary** copies the recommended amount into the Monthly salary field. The admin can still change it.
- [ ] Saving creates the user (201). The new user appears in the listing with 0 orders.
- [ ] A duplicate username shows "Username already taken" on the field (409). Invalid input shows field errors (400).
- [ ] If Claude is unavailable, slow, or returns an implausible answer, a **statistical estimate** is shown instead, clearly labelled as such. Adding the user always still works.
- [ ] Only privacy-protected aggregates are sent to Claude: group sizes and the salary percentiles p10/p25/median/p75/p90, each rounded to the nearest 1,000, and only for groups of at least 10 users. No usernames, individual records, averages, minimums or maximums. With fewer than 10 users in total, Claude is not called and the statistical estimate is returned.
- [ ] Asking twice for the same experience, with no new users added in between, does not call Claude again (cached).

## Assumptions & open questions
- **Assumption:** Claude makes the final proposal (your choice). The backend computes the peer statistics deterministically in SQL and Claude reasons over them. Only counts and rounded percentiles of groups with at least 10 users are sent, so no individual salary can be derived (no averages, which would allow subtraction; no min/max; no exact percentiles of tiny groups). Internal values (validation bounds, the statistical estimate shown to the user) stay exact and are never sent. Sending raw rows would be slower, costlier, leak data, and make the answer less grounded.
- **Assumption:** the statistical fallback exists even though Claude is the primary source. Without it, the Add-user page would break whenever the API is down, rate-limited or misconfigured.
- **Assumption:** the only input is years of experience (your choice). No schema change.
- **Assumption:** there is no authentication in the app today, so both endpoints are open like the existing ones. See Risks.
- **Question:** the model. The recommended default is `claude-opus-5-5` at **low effort**, about $0.01–0.05 per uncached suggestion. `claude-haiku-5-5` is about 40× cheaper and probably good enough for this narrow task. (recommended: start on Opus 5.5; switching is a one-line config change, `salary-advisor.model`)
- **Question:** do you have an Anthropic API key for local testing? Without one, everything still works, but you only ever see the statistical fallback. (recommended: yes, put `ANTHROPIC_API_KEY` in the git-ignored `.env`)

## API contract
All changes are additive; existing endpoints are unchanged.

**1. Create user:** `POST /api/users`
```json
// request
{ "username": "neha.k", "yearsOfExperience": 7, "monthlySalary": 82000.00 }
// 201 Created, Location: /api/users/1001
{ "id": 1001, "username": "neha.k", "orderCount": 0, "monthlySalary": 82000.00, "yearsOfExperience": 7 }
```
- `username`: required, 3–50 chars, `[a-z0-9._-]+`
- `yearsOfExperience`: required, 0–60
- `monthlySalary`: required, > 0, at most 10 integer digits and 2 decimals (matches `DECIMAL(12,2)`)
- **400** ProblemDetail with field errors · **409** username already exists

**2. Get user:** `GET /api/users/{id}`. This makes the `Location` header real. Returns **200** `UserSummary` or **404** ProblemDetail.

**3. Salary recommendation:** `POST /api/salary-recommendations`
```json
// request
{ "yearsOfExperience": 7 }
// 200 OK
{
  "recommendedMonthlySalary": 82000.00,
  "rangeLow": 74000.00,
  "rangeHigh": 90000.00,
  "rationale": "Users with 5–9 years earn a median of 80,500/month; 7 years sits mid-band ...",
  "source": "AI",                 // "AI" | "STATISTICAL"
  "basedOnUsers": 163,
  "model": "claude-opus-5-5"      // null when source = STATISTICAL
}
```
- **400:** invalid `yearsOfExperience`.
- **422:** not enough data, meaning there are no users in the database at all.
- It is `POST`, not `GET`, because every call can cost money. GET requests get prefetched, crawled and retried by proxies.

## Data model & migrations
- **No schema change.**
- **Demo seed data must change.** Today salary and experience are unrelated: salary is `(x*7919) % 120000`, experience is `(x*13) % 31`. Any recommendation on that data is noise. Make salary depend on experience plus spread:
  `monthly_salary = 30000 + years * 3500 + ((x * 7919) % 20000) - 10000`, which gives about 20,000–145,000.
  - Update both `db/seed/h2/V1_1__seed_demo_users.sql` and `db/seed/postgresql/V1_1__seed_demo_users.sql`.
- **Editing `V1_1` is safe only because no persistent database has run it.** H2 is in-memory and rebuilt on every start, and PostgreSQL was never set up: there was no `.env` and no `flyway_schema_history` as of 2026-11-02. **Re-check before editing.** If a PostgreSQL `usersdb` has been migrated since, add `V1_2__correlate_demo_salaries.sql` (an `UPDATE`) in the seed folders instead.
- The test fixture `test-users.sql` stays as is. Recommendation tests insert their own data.

## Backend changes
**Create user** (`user` package)
- `CreateUserRequest` record with Bean Validation and `@Schema`.
- A creation constructor or static factory on `AppUser`.
- `UserService.createUser`:
  - check `existsByUsername`, and throw `UsernameAlreadyExistsException`;
  - also catch `DataIntegrityViolationException` for the race between two simultaneous creates, and turn it into a 409.
- `UserService.findUser`, which throws `UserNotFoundException` → 404.
- Controller: `@PostMapping` returning `ResponseEntity.created(location)`, plus `@GetMapping("/{id}")`, with full Swagger annotations.
- `GlobalExceptionHandler`: 409 and 404 handlers.
- After a successful create, evict the recommendation cache (see below).

**Salary statistics** (new `salary` package)
- `SalaryStatsService` builds the facts sent to Claude:
  - **Overall:** count, p10/p25/median/p75/p90 via SQL `PERCENTILE_CONT`; min/max are fetched for internal validation only and never sent.
  - **Per experience year:** count and the same percentiles, using one SQL `GROUP BY` with `HAVING COUNT(*) >= 10`. At most 61 rows.
  - **Peer band:** users with experience within ±2 years of the input. Compute count and p10/p25/median/p75/p90 in SQL. Widen to ±4, then overall (only if there are at least 10 users in total), if the band has fewer than 10 users.
- `SalaryStats` record, which is what gets serialized into the prompt.
- The statistical fallback is computed from the same data:
  - recommended = peer-band median,
  - range = p25–p75,
  - rationale = a fixed template string.

**Claude integration** (`salary` package)
- **Dependency:** `com.anthropic:anthropic-java`, 3.2.0 or the latest 3.x at implementation time.
- **Config:** `@ConfigurationProperties("salary-advisor")`, validated, with these keys:
  - `enabled` (default true)
  - `model` (default `claude-opus-5-5`)
  - `effort` (default `low`)
  - `timeout` (default 15s)
  - `max-retries` (default 1)
- **API key:** read from `ANTHROPIC_API_KEY` and never logged. Move `spring.config.import` of `.env` into the base `application.yml` so the key works locally without the postgres profile. With no key, advisor = disabled, and the statistical fallback is used.
- **`AnthropicClient` bean:** `AnthropicOkHttpClient.builder()` with the timeout and max retries above. Only created when enabled and a key is present.
- **`ClaudeSalaryAdvisor.propose(int years, SalaryStats stats)`:**
  - Use structured output: `MessageCreateParams.builder().model(...).maxTokens(4000).outputConfig(AiSalaryProposal.class)` with `OutputConfig` effort LOW. `AiSalaryProposal` is a record of `recommendedMonthlySalary`, `rangeLow`, `rangeHigh` and `rationale`, with `@JsonPropertyDescription`, so you get typed JSON with no manual parsing.
  - The system prompt says Claude is a compensation analyst, must ground the proposal in the given statistics, must stay within the observed salary range unless it explains why, and must write a short, plain-English rationale. The stats go in the user message as compact JSON.
  - Use the server-side refusal fallback (`fallbacks: "default"`, beta `server-side-fallback-2026-07-01`), which the Claude API skill recommends by default for Opus 5.5. The Java builder call isn't documented in the skill, so check the SDK repo's `examples/`. Also check `stopReason`: a `refusal` or `max_tokens` stop counts as failure.
  - Catch the typed SDK exceptions most-specific first (`RateLimitException`, then `AnthropicServiceException`, then I/O and timeout) and return `Optional.empty()`, logging the error type but no secrets.
- **No prompt caching.** The prompt is a few hundred tokens, below the minimum cacheable prefix, and the stats change whenever users are added.

**Recommendation orchestration**
- `SalaryRecommendationService.recommend(years)`:
  1. Compute the stats. If there are no users, throw an exception → 422.
  2. Call the advisor.
  3. Validate the answer: `0 < low ≤ recommended ≤ high`, and recommended within [0.5 × observed min, 1.5 × observed max].
  4. If it's valid, return source `AI`; otherwise return the statistical result with source `STATISTICAL`.
- **Response cache:** Spring Cache with a simple in-memory map, keyed by `yearsOfExperience`. The whole cache is evicted on user creation, because new data means a new answer. No new dependency is needed (`spring-boot-starter-cache` is part of Boot).
- `SalaryRecommendationController`: `POST /api/salary-recommendations` with `RecommendSalaryRequest` (`@NotNull @Min(0) @Max(60) Integer yearsOfExperience`), plus Swagger annotations for 200, 400 and 422.

## Frontend changes
- **Models:** `CreateUserRequest`, `SalaryRecommendation` (with `source: 'AI' | 'STATISTICAL'`).
- **Services:**
  - `UserService.createUser()` and `getUser()`.
  - New `SalaryRecommendationService.recommend(years)`.
- **Route:** `/users/new` → lazy-loaded `AddUserComponent`. The listing page gets an **Add user** link-button in the header.
- **`AddUserComponent`** (OnPush, typed reactive form):
  - Fields: `username` (required, pattern, max 50), `yearsOfExperience` (required, integer, 0–60) and `monthlySalary` (required, > 0).
  - **Suggest salary** is enabled only when experience is valid. Clicking it triggers a request through `switchMap`, so a newer click cancels an older request. It shows "Analyzing salaries…" while loading.
  - The suggestion card shows:
    - the amount, the range and the rationale;
    - "Based on N existing users";
    - a badge: **AI suggestion** for `AI`, or **Statistical estimate (AI unavailable)** for `STATISTICAL`;
    - a **Use this salary** button that sets `monthlySalary`.
  - If the experience value changes after a suggestion, the card dims and shows "Suggestion is for 7 years — suggest again".
  - On recommendation failure, show an inline error card. The form stays usable.
  - **Save** uses `exhaustMap` to prevent double submits and disables the button while pending.
    - Success navigates to `/users`.
    - 409 sets an `usernameTaken` error on the username control.
    - 400 maps errors onto fields where possible, otherwise shows a general alert.
- **Accessibility:**
  - labelled inputs, with error text linked through `aria-describedby`;
  - the suggestion card in an `aria-live="polite"` region;
  - all buttons reachable by keyboard.

## Tests
**Backend**
- **Create user:**
  - 201 with Location and body, and the user then shows up in the listing;
  - 400 for blank, too-long or invalid-pattern username, negative or >60 experience, zero or too many digits of salary;
  - 409 for a duplicate username.
- **Get user:** 200, and 404 as ProblemDetail.
- **`SalaryStatsService` (unit):**
  - quartiles on odd and even counts;
  - band widening when the ±2 band has fewer than 10 users;
  - a single user;
  - an empty DB throws.
- **`ClaudeSalaryAdvisor` (unit, mocked `AnthropicClient`):**
  - the prompt contains the stats and **no usernames**;
  - a valid structured response is mapped;
  - `refusal` and `max_tokens` stops give empty;
  - a timeout exception gives empty.
- **Recommendation endpoint** (`@SpringBootTest`, `@MockitoBean ClaudeSalaryAdvisor`):
  - a valid AI answer gives `source=AI`;
  - out-of-range or inconsistent AI values (`low > high`, recommended 10× max) give `STATISTICAL`;
  - an empty advisor result gives `STATISTICAL`;
  - an advisor disabled because there's no key gives `STATISTICAL`;
  - 400 for invalid experience; 422 when no users exist;
  - a second identical call doesn't invoke the advisor again (cache);
  - creating a user evicts the cache.
- **OpenAPI:** documents `POST /api/users`, `GET /api/users/{id}` and `POST /api/salary-recommendations`.

**Frontend**
- Form validation messages per field; Save disabled while invalid.
- Suggest is disabled for invalid experience; it sends `{yearsOfExperience}` and renders the amount, range, rationale and count.
- Badge text for `AI` vs `STATISTICAL`.
- **Use this salary** fills the field.
- Changing experience marks the suggestion stale.
- A recommendation error shows the error card and the form still submits.
- Save posts the right body and navigates; 409 shows "Username already taken"; a double click sends only one request.
- The listing page shows the **Add user** link.

**Manual check (needs your API key, costs a few cents):** one real suggestion at 3, 10 and 25 years. Check that the numbers sit inside the peer range and that the rationale reads sensibly.

## Alternatives considered
- **Statistics only, no Claude.** Free, instant and deterministic. Rejected because you chose Claude, but it's kept as the fallback path.
- **Send raw user rows to Claude.** Simpler code, but it costs more tokens (1000+ rows), is slower, sends personal data, and Claude would be doing arithmetic the database does exactly. Rejected.
- **`claude-haiku-5-5` instead of Opus 5.5.** About 40× cheaper per call. A reasonable switch if cost matters; it's one config value.

## Risks
- **Cost abuse.** There's no auth, so anyone can trigger paid calls. → The per-experience cache, a POST-only endpoint, and an explicit button (no call on every keystroke). Real rate limiting comes with auth, which is out of scope.
- **Implausible or invented numbers.** → Grounded statistics in the prompt, structured output, range validation, and the statistical fallback.
- **Latency of several seconds.** → Loading state, 15 s timeout with 1 retry, then fallback. Worst case is about 30 s before the fallback, so consider lowering the timeout to 10 s after measuring.
- **API key leakage.** → Env var or git-ignored `.env` only; never logged; never sent to the frontend.
- **Misleading demo data.** → The seed fix above. On real data, the quality of suggestions depends on having enough users per experience band, which is why `basedOnUsers` is shown.

## Out of scope
- Authentication, roles and rate limiting
- Editing or deleting users
- Role, department or location inputs (could be added later to improve suggestions)
- Storing recommendation history or analytics
- Streaming the rationale token by token

## Tasks
| # | Agent | Task | Verify | Size |
|---|-------|------|--------|------|
| 1 | backend-engineer | Re-check no persistent DB has run `V1_1`, then make the demo salary depend on experience in both vendor seed files | `cd backend && mvn test`; start the app and check `?sort=yearsOfExperience` shows rising salaries | S |
| 2 | backend-engineer | Create user (`POST /api/users`) + `GET /api/users/{id}`: request DTO, validation, 404/409 handling, Swagger, tests | `cd backend && mvn test` | M |
| 3 | backend-engineer | `SalaryStatsService` + `SalaryStats` + statistical fallback calculation, with unit tests | `cd backend && mvn test` | M |
| 4 | backend-engineer | Claude integration: SDK dependency, `salary-advisor` properties, client bean, `ClaudeSalaryAdvisor` with structured output + refusal fallback + error handling, unit tests with a mocked client | `cd backend && mvn test` | M |
| 5 | backend-engineer | `POST /api/salary-recommendations`: orchestration, AI-answer validation, fallback, cache + eviction on create, Swagger, integration tests | `cd backend && mvn test` | M |
| 6 | angular-architect | Models + services (`createUser`, `getUser`, `recommend`), `/users/new` route, **Add user** button on the listing | `cd frontend && npx ng build` | S |
| 7 | angular-architect | `AddUserComponent`: typed form, suggest flow + suggestion card, use-this-salary, stale state, save with 409 handling, accessibility, specs | `cd frontend && npx ng test --watch=false --browsers=ChromeHeadless` | M |
| 8 | code-reviewer | Review the full change, especially the prompt contents (no PII), AI-output validation, key handling, and Sonar issues | review verdict | S |
| 9 | you + main session | Manual check with a real API key at 3, 10 and 25 years | suggestions look plausible | S |
