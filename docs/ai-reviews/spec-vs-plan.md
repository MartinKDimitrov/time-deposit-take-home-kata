# spec-vs-plan (full) — c4ea358..HEAD

**Which document is which.** Specification: `docs/ASSIGNMENT.md` (verified verbatim against the original client
README — `git show c4ea358:README.md` is byte-identical to `docs/ASSIGNMENT.md`, moved by commit 4ca5339). Plan /
decision record: `docs/PRODUCT.md` + `docs/TRADEOFFS.md`. Supporting: `README.md`, `docs/ARCHITECTURE.md`,
`docs/AI_USAGE.md`, `src/main/resources/openapi/time-deposits.yaml`, and the code where a finding needed it.
Read-only review; nothing was built or run beyond `git` and `grep`.

There is no `docs/SPEC.md`/`docs/PLAN.md`/`docs/DEVPLAN.md` in this repository: the build order exists only as the
commit history. That history is in a sound order (build → contract → schema → use cases → web → concurrency fix),
each step observable when it landed, so the absence of a plan document costs nothing now.

## 1. Coverage table

Every requirement and guideline in `docs/ASSIGNMENT.md`, in document order.

| # | Spec requirement (quoted) | Where the plan addresses it | Status |
| --- | --- | --- | --- |
| R1.1 | "Create a RESTful API endpoint to update the balances of all time deposits" (ASSIGNMENT:11) | PRODUCT.md:64-71 `POST /time-deposits/balance-updates`, A6 (PRODUCT.md:84); openapi:28-38 | covered |
| R1.2 | "Create a RESTful API endpoint to retrieve all time deposits" (ASSIGNMENT:12) | PRODUCT.md:45-62; openapi:13-26 | covered |
| R1.2a | GET schema `id` (ASSIGNMENT:14) | PRODUCT.md:52; openapi:47-50 | covered |
| R1.2b | GET schema `planType` (ASSIGNMENT:15) | PRODUCT.md:53; openapi:51-54 | covered |
| R1.2c | GET schema `balance` (ASSIGNMENT:16) | PRODUCT.md:54, 61 ("JSON number with two decimals"); openapi:55-58 | covered |
| R1.2d | GET schema `days` (ASSIGNMENT:17) | PRODUCT.md:55; openapi:59-63 | covered |
| R1.2e | GET schema `withdrawals` (ASSIGNMENT:18) | PRODUCT.md:56, 61-62; openapi:64-67, 69-84 | **partial** — shape chosen silently, finding 1 |
| R2.1 | "Store all time deposit plans in a database" (ASSIGNMENT:21) | PRODUCT.md:9; TRADEOFFS.md:108-111 ("Flyway owns the schema"); V1__create_time_deposit_tables.sql | **partial** — schema yes, no data path in `src/main`, finding 3 |
| R2.2a | `timeDeposits.id` Integer primary key (ASSIGNMENT:24) | TRADEOFFS.md:102-106 (renamed `time_deposits`); V1:5 `id INTEGER PRIMARY KEY` | covered (rename recorded; no id generation — finding 10) |
| R2.2b | `planType` String (required) (ASSIGNMENT:25) | V1:6 `plan_type VARCHAR(50) NOT NULL` | covered (length silent — finding 10) |
| R2.2c | `days` Integer (required) (ASSIGNMENT:26) | V1:7 `days INTEGER NOT NULL` | covered |
| R2.2d | `balance` Decimal (required) (ASSIGNMENT:27) | TRADEOFFS.md:63-67 (`NUMERIC(19,2)`); V1:8 | covered |
| R2.3a | `withdrawals.id` Integer primary key (ASSIGNMENT:29) | V1:13 | covered |
| R2.3b | `timeDepositId` Integer (foreign key, required) (ASSIGNMENT:30) | V1:14 `time_deposit_id INTEGER NOT NULL REFERENCES time_deposits (id)` | covered (not exposed in the API — finding 1) |
| R2.3c | `amount` Decimal (required) (ASSIGNMENT:31) | V1:15 | covered |
| R2.3d | `date` Date (required) (ASSIGNMENT:32) | V1:16; PRODUCT.md:61 ("ISO-8601 `yyyy-MM-dd`") | covered |
| R3.0 | "calculate monthly interest based on the plan type" (ASSIGNMENT:35) | PRODUCT.md:13-17 `balance × annual rate ÷ 12` | **partial** — annual-vs-monthly reading not recorded, finding 2 |
| R3.1 | "Basic Plan: 1% interest" (ASSIGNMENT:36) | PRODUCT.md:21; BasicInterestPolicy `ANNUAL_RATE = 0.01` | covered |
| R3.2 | "Student Plan: 3% interest (no interest after 1 year)" (ASSIGNMENT:37) | PRODUCT.md:22 and A3 (PRODUCT.md:81); StudentInterestPolicy `LAST_DAY_WITH_INTEREST = 365` + comment | covered |
| R3.3 | "Premium Plan: 5% interest (interest starts after 45 days)" (ASSIGNMENT:38) | PRODUCT.md:23, 34-35; PremiumInterestPolicy `INTEREST_STARTS_AFTER_DAYS = 45` | covered |
| R3.4 | "No interest is applied for the first 30 days for any existing plans" (ASSIGNMENT:39) | A1 (PRODUCT.md:79); TRADEOFFS.md:81-83; calculator `INTEREST_FREE_DAYS = 30` | covered — both readings considered, choice recorded |
| R4.1 | "Do not introduce breaking changes to the shared `TimeDeposit` class or modify the `updateBalance` method signature" (ASSIGNMENT:42) | CLAUDE.md hard constraints 1-2; TRADEOFFS.md:54-61; `TimeDeposit.kt` unchanged vs c4ea358; calculator keeps no-arg constructor | covered |
| R4.2 | "Ensure the design is extensible to accommodate future complexities in interest calculations" (ASSIGNMENT:43) | ARCHITECTURE.md:53-67; TRADEOFFS.md:71-86; test "new plan types are supported by adding an interest policy" | covered, with the limits recorded (TRADEOFFS.md:81-86) |
| R5 | "Adhere to SOLID principles, design patterns, and clean code practices" (ASSIGNMENT:46) | ARCHITECTURE.md (hexagonal, strategy); CLAUDE.md "Code style"; TRADEOFFS.md:69-98 | covered |
| R6.1 | "Set up an AI harness or agent workflow and use it throughout the development" (ASSIGNMENT:49) | AI_USAGE.md:13-38; `.claude/agents/*` and `.githooks/*` are committed (verified `git ls-files`) | covered |
| R6.2 | "Briefly document the tools and setup used" (ASSIGNMENT:50) | AI_USAGE.md:5-27 | covered |
| R6.3 | "Ensure your AI setup is practical and reproducible" (ASSIGNMENT:51) | AI_USAGE.md:40-47 ("Reproduce it") | covered (version drift already filed by docs-claims-audit 5) |
| R6.4 | "Include any custom rules, system prompts, or agent configurations used" (ASSIGNMENT:52) | `CLAUDE.md`, `.claude/agents/` (7 files), `.claude/commands/` (2 files) — all tracked | covered |
| R6.5 | "Include a brief summary of which parts of the solution were AI-assisted and why" (ASSIGNMENT:53) | AI_USAGE.md:49-59 ("Who did what"), 61-79 | covered |
| G1 | "The existing `TimeDepositCalculator.updateBalance` method is functioning correctly. Ensure its behavior remains unchanged" (ASSIGNMENT:56) | PRODUCT.md:37-38; TRADEOFFS.md:54-61; `TimeDepositCalculatorTest`; `docs/ai-reviews/regression-hunt.md` | covered — strongest part of the work |
| G2 | "must include exactly two API endpoints. Do not develop additional endpoints" (ASSIGNMENT:57) | PRODUCT.md:43; CLAUDE.md hard constraint 3; `TimeDepositModule.kt` also installs `swaggerUI(path = "swagger")` | **partial** — see finding 7 and docs-claims-audit 2 |
| G3 | "Do not create a pull request or a new branch in the ikigai-digital repository. Instead, fork" (ASSIGNMENT:58) | Nowhere in PRODUCT/TRADEOFFS; `origin` is the fork, `upstream` is ikigai-digital with a push URL | **partial** — finding 6 |
| G4 | "Handling invalid input or exceptions is not required" (ASSIGNMENT:59) | PRODUCT.md:86-90; CLAUDE.md hard constraint 5 | covered (but no stated behaviour on failure — finding 8) |
| G5 | "Use any tools, frameworks, or libraries you find suitable" (ASSIGNMENT:60) | TRADEOFFS.md:14-49 (Ktor/Exposed, Maven, manual wiring) | covered |
| G6 | "In case of ambiguity, make logical assumptions and **justify them in code comments**" (ASSIGNMENT:61) | PRODUCT.md:73-85 (A1-A6) + 6 code comments (grep: calculator x3, repository, GET service, StudentInterestPolicy) | **partial** — A4, A5, A6 have no code comment, finding 4 |
| S1 | "Use an OpenAPI Swagger contract" (ASSIGNMENT:64) | TRADEOFFS.md:38-43; ARCHITECTURE.md:103-108; `openapi/time-deposits.yaml` | covered |
| S2 | "Embrace Hexagonal Architecture" (ASSIGNMENT:65) | ARCHITECTURE.md:1-51; TRADEOFFS.md:32-37 | covered |
| S3 | "Follow atomic commit practices" (ASSIGNMENT:66) | CLAUDE.md "Workflow"; 30 commits, one logical change each | covered |
| S4 | "Utilize testcontainers" (ASSIGNMENT:67) | ARCHITECTURE.md:110-119; TRADEOFFS.md:138-151; `TimeDepositApiIT` | covered |
| S5 | "Leverage AI-assisted development tools for code generation, testing, and refactoring" (ASSIGNMENT:68) | AI_USAGE.md:49-79 | covered |
| B1 | "Provide clear instructions on how to trigger the endpoints using the Swagger contract" (ASSIGNMENT:71) | README.md:20-56 | covered (the `/swagger` route itself is untested — finding 7) |
| B2 | "Email the link to your public GitHub repository" (ASSIGNMENT:72) | human action, outside the repository | n/a |

## 2. Findings

### 1. Medium — The content and shape of `withdrawals` in the GET response is the biggest unrecorded interpretation
- **Where**: ASSIGNMENT.md:13-18 ("a list of all time deposits with the following schema: ... `withdrawals`") vs PRODUCT.md:49-62 (JSON example with a nested array) and openapi:64-84; `TimeDepositResponses.kt:11-35`.
- **Problem**: the spec names `withdrawals` as a field and gives it no type. Reading A: a nested array of withdrawal objects. Reading B: a scalar — the number of withdrawals, or the total amount withdrawn (the spec's own `timeDeposits` table has no `withdrawals` column, so either is derivable). The plan silently chose A, and inside A silently dropped `timeDepositId`, which the spec lists as a required column (ASSIGNMENT.md:30). PRODUCT.md's Assumptions table (A1-A6) does not mention the choice at all; only the example shows it.
- **Why it matters**: this is the one field whose shape a client reviewer compares against their own expectation, and the cost of being wrong is contract + DTO + mapper + every integration assertion + the `additionalProperties: false` guarantee. Every other open question in this kata costs a line of documentation; this one costs a slice.
- **Action**: add an assumption "A7: `withdrawals` is the full list of withdrawal rows for the deposit (`id`, `amount`, `date`); `timeDepositId` is omitted because it is implied by nesting" to PRODUCT.md, so the decision is defended rather than discovered.

### 2. Medium — "1% interest" is read as an annual rate divided by twelve, and that reading is never stated as an assumption
- **Where**: ASSIGNMENT.md:35-38 ("Implement logic to calculate monthly interest based on the plan type: Basic Plan: 1% interest") vs PRODUCT.md:13-17 ("monthly interest = balance × annual rate ÷ 12") and the table header "Annual rate" (PRODUCT.md:19).
- **Problem**: the spec says "monthly interest" and "1% interest" in the same breath. Reading A: 1% per year, so 1/12% per month (the plan's). Reading B: 1% per month. The plan's only justification is PRODUCT.md:37 ("These numbers come from the original `TimeDepositCalculator.updateBalance`, which the assignment says is correct") — correct reasoning, but it appears as a footnote to a table rather than as a recorded assumption, and the word "annual" never appears in the spec.
- **Why it matters**: it is a factor-of-twelve difference in the headline number, and the reader of PRODUCT.md cannot tell whether the author noticed the ambiguity or just copied the code. A1-A6 record six smaller decisions and not this one.
- **Action**: one row in the Assumptions table: "The plan percentages are annual rates; one update adds one twelfth. Why: the original code computes `balance * rate / 12` and the assignment pins its behaviour."

### 3. Medium — "Store all time deposit plans in a database" has no data path in the shipped artifact, and PRODUCT.md describes one that does not exist
- **Where**: ASSIGNMENT.md:21 ("Store all time deposit plans in a database") vs PRODUCT.md:88-90 ("data is loaded by migrations or by the tests"); `DatabaseSetup.kt:12` (`migrationLocations: List<String> = listOf("classpath:db/migration")`), and `src/main/resources/db/migration/` contains only `V1__create_time_deposit_tables.sql`. The only data file, `R__demo_data.sql`, is in `src/test/resources/db/seed`, reachable only from `LocalApplication` (`make run`); README.md:61 says the production entry point "starts with an empty database".
- **Problem**: no migration in `src/main` loads deposits, and the two-endpoint constraint forbids a create endpoint, so in a real deployment `GET` returns `[]` and `POST` is a no-op forever. PRODUCT.md's claim that data is "loaded by migrations" is true only of the test-scoped seed.
- **Why it matters**: a reviewer who follows README.md:58-68 ("Against your own PostgreSQL") sees an empty service and may read R2.1 as unmet. Cheap now, awkward to explain later.
- **Action**: either reword PRODUCT.md to "data is inserted directly into the database; `make run` loads a demo seed from the test resources", or move the seed behind a documented `db/seed` location on the main classpath. Author's choice; the first is a sentence.

### 4. Medium — The spec asks for assumptions justified "in code comments"; three of the six live only in the documents
- **Where**: ASSIGNMENT.md:61 ("In case of ambiguity, make logical assumptions and justify them in code comments") vs PRODUCT.md:73-85 (A1-A6). Code comments exist for A2 (`TimeDepositCalculator.kt:32`), A3 (`StudentInterestPolicy.kt:14`) and A1 (KDoc in `InterestPolicy.kt`). A4 (`days` not changed), A5 (withdrawals are history only) and A6 (`POST` + `204`) appear in no source file — grep over `src/main/kotlin` finds six comments in total, none of them these.
- **Problem**: the strongest interpretive decisions are exactly the ones a reader of the code cannot see: nothing beside `UpdateAllBalancesService` says the age is deliberately not advanced, nothing beside `TimeDepositRoutes` says `204` is deliberate, nothing beside the withdrawal mapping says withdrawals never affect interest.
- **Why it matters**: it is a graded requirement, phrased imperatively, and the fix is three one-line comments; after handover it is a mark lost for free.
- **Action**: a comment at `UpdateAllBalancesService.updateAllBalances` (A4, A5) and at the `post("/balance-updates")` route (A6), each pointing at `docs/PRODUCT.md#assumptions`.

### 5. Medium — A4 makes `days` someone else's job and the plan never says whose; with no clock, boundaries can never be crossed
- **Where**: PRODUCT.md:82 ("A4 | A balance update does not change `days`. | The age of a deposit is maintained outside this service; the original calculator never changes it, and the assignment defines no clock") vs ASSIGNMENT.md:38-39, whose rules ("interest starts after 45 days", "no interest after 1 year") are entirely functions of age.
- **Problem**: no component in or around the service advances `days`, and there is no endpoint, job or migration that could. The demo data makes this visible: deposit 5 (`premium`, 40 days) will never start earning interest, deposit 4 (`student`, 400 days) will never stop being excluded — no matter how many months of interest are applied. The assumption is recorded; its operational consequence is not.
- **Why it matters**: it is the assumption most likely to be challenged in a review conversation ("so how does a deposit ever reach day 46?"), and the answer is architectural, not a bug fix: a clock would move into the domain and `days` would become derived from a start date.
- **Action**: extend A4 with the consequence and the owner: "nothing in this service advances `days`; the writer of `time_deposits.days` is outside the system boundary. A service that owned the age would store an opening date and derive `days` from a clock port."

### 6. Low — The spec forbids pushing a branch to the ikigai-digital repository; the clone keeps a push-enabled remote to it
- **Where**: ASSIGNMENT.md:58 ("**Do not** create a pull request or a new branch in the ikigai-digital repository. Instead, fork the repository into your own GitHub repository") vs `git remote -v`: `upstream https://github.com/ikigai-digital/time-deposit-take-home-kata.git (push)`. Neither PRODUCT.md nor TRADEOFFS.md mentions the remote layout.
- **Problem**: the work is correctly on `origin` (the fork), but a single mistyped `git push upstream main` satisfies the one thing the spec explicitly forbids. The pre-push hook reviews content, not destination.
- **Why it matters**: low probability, unrecoverable embarrassment, and the fix is one command — the asymmetry is what makes it worth naming.
- **Action**: `git remote set-url --push upstream no_push` (not applied here: this review does not touch the repository), and one line in README or TRADEOFFS recording that `upstream` is fetch-only by intent.

### 7. Low — `/swagger` carries a submission requirement, is the single exception to "exactly two endpoints", and no test touches it
- **Where**: ASSIGNMENT.md:57 ("exactly two API endpoints. Do not develop additional endpoints") and ASSIGNMENT.md:71 ("clear instructions on how to trigger the endpoints using the Swagger contract") vs PRODUCT.md:42-43 ("served by Swagger UI at `http://localhost:8080/swagger`. There are exactly two endpoints") and `TimeDepositModule.kt` (`swaggerUI(path = "swagger", swaggerFile = "openapi/time-deposits.yaml")`).
- **Problem**: the Swagger route is both the exception to the headline constraint — stated as absolute in PRODUCT.md:43 and CLAUDE.md hard constraint 3 — and the mechanism the submission instruction depends on. `TimeDepositApiIT` requests only the two API paths; a wrong `swaggerFile` path or a missing dependency would be found by a human opening a browser, which is exactly who the instruction is written for. (The wording half of this is already filed as docs-claims-audit finding 2; the untested half is not.)
- **Why it matters**: the first thing the client does is open `/swagger`. It is the only user-facing path with no automated check.
- **Action**: one integration assertion that `GET /swagger` returns 200 and the served document is the contract; and qualify the "exactly two" sentences as "two API endpoints, plus Swagger UI".

### 8. Low — Unhappy paths with no recorded answer: an empty database, and the database being unreachable
- **Where**: PRODUCT.md:86-90 ("the service does not validate input or handle errors beyond what the frameworks do") and openapi:18-38, which declares only `200` and `204` — no 4xx or 5xx response at all.
- **Problem**: the spec permits this (ASSIGNMENT.md:59), but the plan never states what a caller sees. Two cases are reachable without any invalid input: (a) empty table — `findAllForUpdate()` returns an empty list, `updateBalance(emptyList())` is a no-op, the response is `204`, and `GET` returns `[]`; neither is stated in PRODUCT.md nor asserted anywhere; (b) PostgreSQL unreachable or the transaction rolling back — the caller gets Ktor's default 500, which is outside the contract that the integration tests enforce with `additionalProperties: false`.
- **Why it matters**: (a) is one sentence plus one cheap test and removes the only "is it broken or just empty?" question a demo can raise; (b) is a documented limit, not work.
- **Action**: add to PRODUCT.md: "An empty database returns `[]` and `204`. A database failure surfaces as the framework's 500; no error schema is defined, as the assignment puts error handling out of scope."

### 9. Low — Invented scope: concurrency safety, deployment plumbing and the removal of four language ports
- **Where**: the spec asks for none of these. PRODUCT.md:70-71 ("Safe when called concurrently. Two overlapping calls run one after the other") and TRADEOFFS.md:113-125 (`SELECT … FOR UPDATE`, lock ordering) answer a question ASSIGNMENT.md never poses; `Application.kt` (`DB_URL`/`DB_USER`/`DB_PASSWORD` with silent localhost defaults, TRADEOFFS.md:127-131), Flyway and Hikari are deployment concerns the spec does not mention; commit 4ca5339 deleted the C#, Java, Python and TypeScript implementations that were part of "the existing codebase" (ASSIGNMENT.md:6).
- **Problem**: all three are defensible — the locking is the most interesting engineering in the repository and is well recorded — but they are the whole cost of the most expensive test in the suite (a second JDBC connection polling `pg_stat_activity`) and of the hardest-to-verify claim in the docs (see docs-claims-audit finding 1). The deletion of the four ports is recorded only in a commit message; TRADEOFFS.md never mentions it, so a reviewer who looks for the Java version finds no rationale in the documents.
- **Why it matters**: unbilled work is fine when it is visible. One line in TRADEOFFS.md ("the kata ships five language ports; only Kotlin is solved, the rest were removed to keep one buildable project") closes the only gap.
- **Action**: record the Kotlin-only decision in TRADEOFFS.md; keep the locking and say plainly that it goes beyond the assignment.

### 10. Low — Silent decisions worth writing down before they turn into surprises
- **Where**: all in code or the contract, none in PRODUCT.md or TRADEOFFS.md.
- **Problem**: (a) `GET` is **ordered by id** — openapi:20 ("All time deposits, ordered by id") and `ExposedTimeDepositRepository.findAll()`; the spec asks for no order, and the contract has now promised one. (b) **Ids are not generated** — `V1:5 id INTEGER PRIMARY KEY`, no identity or sequence, so every inserter picks ids by hand (the demo seed does); the spec says only "Integer (primary key)". (c) **`plan_type VARCHAR(50)`** — the spec says "String"; 50 is arbitrary and unexplained. (d) **Two different `Double`-to-decimal conversions**: the calculator uses `BigDecimal(amount)` (deliberate, TRADEOFFS.md:54-61) while the write path uses `BigDecimal.valueOf` in `Money.kt`; they agree for cent-aligned values, and the reason they differ is recorded only for the calculator.
- **Why it matters**: each is almost certainly right and each is a question someone will ask; answering them in the record costs four lines and prevents a re-litigation.
- **Action**: a short "Decided in passing" list in TRADEOFFS.md holding (a)-(d).

## 3. Missing

Nothing in `docs/ASSIGNMENT.md` is wholly unbuilt. The partials are R1.2e (finding 1), R2.1 (finding 3), R3.0 (finding 2), G2 (finding 7), G3 (finding 6) and G6 (finding 4) — in every case the code exists and the gap is in the record.

## 4. Invented

| Built | Asked for by the spec? | Recorded? |
| --- | --- | --- |
| Row locking, lock ordering, the concurrency guarantee and its test | no | yes — TRADEOFFS.md:113-125 |
| Flyway migrations, Hikari pool, env-var production entry point with localhost defaults | no | partly — TRADEOFFS.md:108-136 |
| Swagger UI route | implied by ASSIGNMENT.md:64, 71 | yes, but as "exactly two endpoints" (finding 7) |
| `make run` + `LocalApplication` + demo seed on Testcontainers | no (serves ASSIGNMENT.md:71) | yes — TRADEOFFS.md:150-151 |
| Two-level AI quality gate, 7 agents, hooks, `docs/ai-reviews/` | yes — ASSIGNMENT.md:48-53 | yes — AI_USAGE.md, TRADEOFFS.md:153-179 |
| Removal of the C#/Java/Python/TypeScript ports | no | commit message only (finding 9) |
| `GET` ordered by id, `VARCHAR(50)`, manual ids | no | no (finding 10) |

## 5. Ambiguities that change the build

| Spec sentence | Reading A | Reading B | Plan chose | Recorded? |
| --- | --- | --- | --- | --- |
| "1% interest" / "monthly interest" (ASSIGNMENT:35-36) | annual rate ÷ 12 | 1% per month | A | implied only (finding 2) |
| "`withdrawals`" in the GET schema (ASSIGNMENT:18) | nested array of rows | count or total amount | A | by example only (finding 1) |
| "for any existing plans" (ASSIGNMENT:39) | every plan, now and later | only today's three plans | A | **yes** — A1, TRADEOFFS.md:81-83 |
| "no interest after 1 year" (ASSIGNMENT:37) | pays while days ≤ 365 | stops at day 365 | A | **yes** — A3 + code comment |
| "interest starts after 45 days" (ASSIGNMENT:38) | pays from day 46 | pays from day 45 | A | **yes** — PRODUCT.md:34-35 examples |
| "update the balances of all time deposits" (ASSIGNMENT:11) | one call = one month | recompute for elapsed time | A | **yes** — PRODUCT.md:66-68 + A4 |
| "Store all ... plans in a database" (ASSIGNMENT:21) | provide the schema | also ship the data | A | partly (finding 3) |

## 6. Testability

- Independently grounded: `updateBalance` behaviour (characterization tests written against the original code before the refactor, plus the old-vs-new comparison in `docs/ai-reviews/regression-hunt.md`) and the API shape (every response validated against the contract). These are the two requirements that most needed real ground truth and both have it.
- Circular by construction: the interest figures in PRODUCT.md and README come from the same code the tests pin. The only external anchor is the spec's "1%/3%/5%" and the 30/45/365 boundaries — which is why finding 2 matters: with the annual reading written down, the chain spec → assumption → test is complete.
- Unobservable: "extensible to accommodate future complexities" has a test for the one complexity the design supports (a new plan type); the two it does not support (a plan paying before day 31, interest depending on withdrawals) are recorded as limits in TRADEOFFS.md:81-86 rather than tested — correct, but it means R4.2 is demonstrated narrowly.
- Untested paths that a human is expected to exercise: `/swagger` (finding 7), the `make run` demo table in README.md:39-47, and the empty-database case (finding 8).

## 7. What will break first

1. **The age of a deposit** (finding 5). The first real question asked of this service is "apply this month's interest", and the second is "why is deposit 5 still at 40 days?". No component owns `days`.
2. **An empty or externally populated database** (finding 3). The documented production path starts empty, and nothing but hand-written SQL can fill it; the first deployment demo shows `[]`.
3. **`withdrawals` meaning something else to the client** (finding 1). The one place where a different reading means a new slice rather than a new sentence.
4. At scale, the recorded limit arrives in this order: the whole table is loaded and row-locked per update (TRADEOFFS.md:113-125), then one `UPDATE` per row, then the 10-connection pool against 64 `Dispatchers.IO` threads (TRADEOFFS.md:45-49). All three are already written down, which is why they are fourth and not first.

## 8. Solid and complete — start here without further thought

- **The interest domain.** R3.1-R3.4 and R4.1-R4.2 are fully covered, the three genuinely hard boundary ambiguities (30/31, 45/46, 365/366) are each resolved, justified and pinned by tests, and the original behaviour is preserved with independent evidence. Nothing in this review asks for a change here.
- **The contract and the two endpoints.** The OpenAPI file, the DTOs, `additionalProperties: false` and contract validation in every integration test close the drift question that usually stays open in a hand-written Ktor service.
- **The database schema.** R2.2 and R2.3 are field-for-field complete, and the one deviation from the spec (snake_case) is recorded with its reason.
- **Requirement 6 (AI harness).** All five sub-requirements are answered, and — unusually — the configuration is committed rather than described, so "reproducible" is checkable.
- **The record itself.** A1-A6 with reasons, and TRADEOFFS.md attributing accepted findings to the agent that raised them, is why this review is a list of sentences to add rather than a list of decisions to make.
