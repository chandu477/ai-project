# Plan: Half-year experience and half-year salary bands

**Date:** 2026-11-02 · **Status:** Approved (decisions from the user: half-year steps · band average + Claude · widen to neighbours)

Extends `2026-11-02-ai-salary-recommendation.md`.

## Goal
Experience is recorded in half-year steps (0, 0.5, 1 … 60). The salary suggestion is based on users in the **same half-year band**, e.g. 2.5 to 3 years, instead of a wide ±2-year window.

## Definitions
- **Valid experience:** 0–60 in steps of 0.5 (`x * 2` is a whole number).
- **Band** for experience `x`: the half-open range `[x, x + 0.5)`. With half-step data, that means the users whose experience equals `x`. It is labelled "x to x + 0.5 years", e.g. "2.5 to 3 years".
- **Widening:** if a band has fewer than **5** users, add one neighbouring half-year band on each side, giving `[x − 0.5, x + 1.0)`, then `[x − 1.0, x + 1.5)`, and so on.
  - Clamp the lower end at 0.
  - Stop as soon as there are at least 5 users.
  - If even the whole range 0–60.5 has fewer than 5 users, use all users. If there are no users at all, return 422 as today.

## Acceptance criteria
- [ ] The Add-user form accepts 2.5 and rejects 2.3 with "Use steps of 0.5 years" (same message from the backend 400).
- [ ] The listing shows experience like `2.5` and `12` and still sorts by it. It still shows 20 users per page.
- [ ] For 2.5 years, the statistical estimate is the **average** monthly salary of users with exactly 2.5 years. The range is that band's 25th–75th percentile. The card says e.g. "Based on 16 users with 2.5 to 3 years".
- [ ] With a sparse band, the card shows the widened range actually used, e.g. "2 to 3.5 years".
- [ ] Claude receives the band figures and proposes the final number. If Claude is unavailable or its answer is invalid, the band-average estimate is shown, labelled "Statistical estimate".

## API contract (changes)
- **`yearsOfExperience`** becomes a decimal number on all endpoints: the request/response of `POST /api/users`, `GET /api/users[/{id}]`, and `POST /api/salary-recommendations`. Examples: `2.5`, `7`, `7.0`.
  - Out of range or not a multiple of 0.5 → **400** with `errors.yearsOfExperience`.
  - The existing "must be a whole number" rule is replaced by the 0.5-step rule.
- **`POST /api/salary-recommendations` response:** additive fields `bandFromYears` and `bandToYears` (numbers, the band actually used, end exclusive). `basedOnUsers` is the number of users in that band.
- The sort parameter is unchanged (`yearsOfExperience`).

## Data model & migrations
- **New migration `db/migration/V2__half_year_experience.sql`:**
  - Change `years_of_experience` to `NUMERIC(3,1)` using `ALTER TABLE … ALTER COLUMN … SET DATA TYPE`, which both H2 and PostgreSQL accept.
  - Add a named CHECK that it is between 0 and 60 and a half-year step.
  - Existing whole-year values are preserved.
- **New demo seed `V2_1__half_year_demo_experience.sql`,** in both `db/seed/h2/` and `db/seed/postgresql/`:
  - Only for the original seed rows (`id <= 1000` AND the seed username pattern), set experience to `((id * 13) % 61) / 2.0`, giving 0–30 in half years.
  - Recompute the demo salary as `30000 + years * 3500 + ((id * 7919) % 20000) - 10000`, so salary keeps rising with experience.
- The `AppUser` field and DTOs use `BigDecimal` for experience. Normalise it to scale 1 for comparisons and cache keys.

## Backend
- **Validation:** `@NotNull`, `@DecimalMin("0")`, `@DecimalMax("60")`, plus a 0.5-step check (a custom constraint or a `@AssertTrue` method) with the message "Use steps of 0.5 years".
- **Band stats (SQL, `SalaryStatsRepository`):**
  - count, average, p25, p75, and min/max (min/max for internal validation only) for `years_of_experience >= :from AND years_of_experience < :to`;
  - widening loop in `SalaryStatsService`.
- **Statistical estimate:** recommended = band average; range = band p25–p75; rationale e.g. "Statistical estimate: average monthly salary of 16 users with 2.5 to 3 years of experience."
- **Prompt to Claude.** The privacy rules from the previous review still apply:
  - The **target band**, widened independently to at least **10** users for the prompt, is sent with users, **average**, p10/p25/median/p75/p90. Every figure is rounded to 1,000.
  - The average is sent **only for this one group.** No other group in the prompt carries an average, so no subtraction attack is possible.
  - Neighbouring half-year bands with at least 10 users are sent with percentiles only, rounded.
  - The overall distribution is sent with percentiles only, rounded.
  - If there are fewer than 10 users in total, there is no Claude call.
  - The system prompt tells Claude to anchor on the same-band average.
- **Cache and in-flight coalescing** are keyed by the normalised half-year value (e.g. `x * 2` as an int).
- **Tests:**
  - 0.5-step validation on both endpoints (2.5 is OK; 2.3, −0.5 and 60.5 give 400);
  - band selection (exact band, widening, clamping at 0, the all-users fallback);
  - average and percentile values;
  - `bandFromYears`/`bandToYears` in the response;
  - the prompt contains exactly one average, everything rounded, groups of at least 10;
  - the migration keeps existing whole-year values;
  - existing tests updated for the decimal type.

## Frontend
- **Model:** `yearsOfExperience: number` (unchanged type); new `bandFromYears` and `bandToYears` on `SalaryRecommendation`.
- **Form:** the experience input uses `step="0.5"`, min 0, max 60. The validator rejects non-half-steps with "Use steps of 0.5 years", replacing the integer validator.
- **Suggestion card:** "Based on N users with {from} to {to} years". Format numbers without a trailing `.0`.
- **Listing:** experience is shown as `2.5` / `12`, with no change to sorting or page size (20).
- **Specs:** step validation, card band text, listing formatting.

## Out of scope
- Month-level precision, and changing the page size or the listing columns.

## Tasks
| # | Agent | Task | Verify |
|---|-------|------|--------|
| 1 | backend-engineer | Migration + seed, BigDecimal experience, 0.5-step validation, band stats + widening, estimate, prompt, response fields, tests | `cd backend && mvn test` |
| 2 | angular-architect | Model, 0.5-step form validation, card band text, listing formatting, specs | `npx ng build` + `npx ng test --watch=false --browsers=ChromeHeadless` |
| 3 | code-reviewer | Review the change | verdict |
