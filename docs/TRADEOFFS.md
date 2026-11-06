# Trade-offs and assumptions

Decisions taken while solving the kata, with the reason and what was given up. Findings from the AI review agents
(`docs/ai-reviews/`) that were accepted rather than fixed are recorded here as well, with their source. "Push review"
means the quick review that runs on every `git push`; its reports are overwritten by later pushes and remain in the
git history.

## Assumptions

The business assumptions (A1–A6: the 30-day rule for every plan, case-sensitive plan types, the student year, `days`
not incremented, withdrawals as history, `POST` with `204`) are listed with their reasons in
[`PRODUCT.md`](PRODUCT.md#assumptions). A1 was raised by adversarial-architecture finding 4.

## Platform

### Ktor and Exposed, not Spring

The service has two endpoints and one aggregate. Ktor and Exposed are Kotlin-first, start in well under a second and
keep the wiring explicit. The assignment allows any framework. What was given up: the conventions and ecosystem of a
larger framework (declarative transactions, actuator, a DI container), none of which this service needs.

### Manual wiring, no DI framework

`config/TimeDepositModule.kt` creates every object in about a dozen lines. A container would add a dependency and
indirection for a graph this small. If the graph grows, Koin is the natural next step.

### Maven, kept from the provided project

The assignment ships a Maven project; changing the build tool would be a large change that solves nothing in the
assignment. Gradle with the Kotlin DSL would be the idiomatic choice for a new Kotlin service.

### Single module

Hexagonal layers are packages in one module. Separate modules would let the compiler enforce the dependency
direction, at the cost of four build files for a few hundred lines of code. The rule is held by review (see "No
layering test").

### Contract first through validation, not code generation

The OpenAPI file is written first. For Ktor, `openapi-generator` produces a whole server rather than interfaces to
implement, so the routes and DTOs are hand-written and the integration tests validate every response against the
contract instead. The contract therefore cannot drift silently; what it does not give is a compile error when a route
is missing — the test fails instead.

### Blocking JDBC on `Dispatchers.IO`

Exposed JDBC blocks, so the web adapter moves each use case call to `Dispatchers.IO`, keeping the use cases plain
functions. `Dispatchers.IO` allows 64 threads and the Hikari pool has 10 connections; extra requests wait for a
connection, which is acceptable at this scale. Source: adversarial-code (push review).

## Money

### `Double` inside `TimeDeposit` and the calculator

`TimeDeposit.balance` is a `Double` and the class is shared, so it cannot change. The calculator keeps the original
arithmetic (`balance * rate / 12`) and rounding (`BigDecimal(Double)` with `HALF_UP`), because the assignment requires
the same results. `BigDecimal.valueOf` would be the textbook choice, but it changes real balances (18.00 would get
18.02 instead of 18.01), so the characterization tests pin `BigDecimal(Double)` on purpose.

A `Double` holds whole cents exactly up to about 9 × 10¹³; larger balances would lose cents. Far beyond any real
deposit; the limit comes with the shared class. Source: adversarial-code (push review).

### `BigDecimal` with two decimals everywhere else

Outside the shared class money is `BigDecimal` with scale 2 (`NUMERIC(19,2)` in the database, a JSON number with two
decimals in the API). Long cents were considered and rejected: every value would still have to be converted from and
to the `Double` the shared class requires, adding a second rounding step without removing the first.

## Domain

### Interest rules as strategies

Each plan is an `InterestPolicy`; the calculator looks the policy up by plan type. A new plan is a new class.
Accepted consequences:

- **Duplicate plan types:** the last policy in the list wins, silently. This can only happen through constructor
  injection and is documented in the calculator's KDoc. No guard was added, because rejecting invalid input is out of
  scope. Sources: adversarial-architecture finding 9, verify-findings-closed, test-quality (push review).
- **Injection replaces the defaults:** `TimeDepositCalculator(listOf(vip))` drops the built-in plans. Callers pass
  `defaultPolicies() + vip`, as the extensibility test does. Source: adversarial-architecture finding 9.
- **The 30-day rule lives in the calculator:** policies are `internal`, so the only way to ask one is through the
  calculator, which applies the rule first. A plan that should pay earlier would move the rule into the policies.
  Sources: adversarial-architecture findings 4 and 6.
- **Policies receive the mutable `TimeDeposit`:** passing only `balance` and `days` would be cleaner but widen the
  change for no current benefit. The calculator computes the interest before it reads the balance, as the original
  did. Source: adversarial-code finding 2.

### `TimeDeposit` and `TimeDepositCalculator` stay in the root package

Both are pinned by the assignment, so they stay in `org.ikigaidigital` and are treated as domain types. The root
package and `domain/interest` reference each other; there is no class-level cycle. Source: adversarial-architecture
findings 1 and 10.

### No layering test

The dependency rule (nothing in `domain/` or `application/` imports Ktor or Exposed) is checked by review, not by a
test. An ArchUnit or Konsist test is the first follow-up worth adding now that the adapters exist; it was left out to
keep the dependency list short. Source: adversarial-architecture finding 10.

## Persistence

### snake_case tables

The assignment names the tables `timeDeposits` and `withdrawals` with camelCase columns. PostgreSQL folds unquoted
identifiers to lower case, so camelCase would need quotes in every query. The tables are `time_deposits` and
`withdrawals` with snake_case columns; the API keeps the camelCase names.

### Flyway owns the schema

The schema is created by a versioned migration, not by Exposed. The schema is reviewable SQL, and the same migration
runs in tests, locally and in production.

### Balance update: all deposits in memory, locked, one UPDATE per row

The update loads every deposit, applies `updateBalance` (whose signature takes a list) and writes each balance back.
Accepted consequences:

- **Memory and time grow with the number of deposits.** Fine for a kata; at scale the update would run in batches.
- **One `UPDATE` per row, including unchanged rows.** Simple and correct; a batch update is an optimisation for later.
  Source: adversarial-code (push review).
- **Every row is locked** (`SELECT … FOR UPDATE` without a filter) for the duration of the update. This is what makes
  concurrent updates safe; it also blocks other writers to those rows meanwhile. Source: verify-findings-closed (push
  review).
- **Reads do not wait for the lock.** A `GET` during an update returns the last committed balances (PostgreSQL READ
  COMMITTED), never a half-updated state. Source: adversarial-code (push review).

### Default database credentials in `Application.kt`

Without `DB_URL`, `DB_USER` and `DB_PASSWORD` the production entry point falls back to a local
`timedeposits/timedeposits` database. Convenient for running locally; a real deployment sets the variables. Source:
adversarial-code (push review).

### No cleanup if the migration fails at start-up

If Flyway fails, the application fails to start and the JVM exits, so the pool is not closed explicitly. Source:
adversarial-code (push review).

## Testing

- **Unit and integration tests, no mocks.** The domain has no dependencies to mock; the integration tests use the
  real database, because an in-memory substitute would not show PostgreSQL locking or types.
- **Integration data sits on the paying side of each boundary** (31, 46 and 366 days). The boundaries themselves are
  pinned in the unit tests; the integration tests check the wiring. Source: test-quality (push review).
- **Lock order is not tested.** That updates lock rows in id order (so two updates cannot deadlock) is held by the
  code and a comment; a deadlock cannot be provoked reliably in a test. Source: test-quality (push review).
- **The lock test waits for any blocked session** in `pg_stat_activity`. Only the test's own sessions run against the
  test database, so this is the update. Its timeout path is not tested. Source: test-quality (push review).
- **The lock test was verified both ways:** with `.forUpdate()` removed it fails (1000.83 instead of 2001.67), with
  it restored it passes.
- **`make run` uses Testcontainers** instead of a Docker Compose file: one less file to maintain, and the demo
  database is the same image the tests use.

## AI quality gate

### Two levels: a quick gate on push, a thorough review on purpose

The pre-push hook runs `make verify` and then the AI gate in push mode: read-only review agents on Sonnet, in
parallel, stopped after three minutes. Running code to prove a finding (mutation testing, differential tests, hook
experiments), the slow agents (regression, architecture, spec and docs review) and Opus are kept for
`/quality-gate full`, run once before handover. The first version reviewed everything on every push and took over 15
minutes for a two-class change, mostly model time rather than build time. Sonnet on push is also cheaper for a
review that runs many times a day; Opus is kept for the one thorough review. What was given up: findings at push
time are marked SUSPECTED until `full` proves them or a human checks them.

### The stop after a review is a reminder, not a block

When the gate writes reports the push stops; the identical second push goes through even if a finding is still
open. A hard block would need a machine-readable verdict and a way to override it, and would loop if the reports
themselves were reviewed again. Source: adversarial-architecture finding 2.

### Known limits of the hooks

- **Single branch:** a push of several refs at once is judged by the last ref only. This project uses `main` only.
  Source: adversarial-architecture finding 3.
- **Working tree, not the pushed commit:** `make verify` builds the working tree, so unstaged changes can make a
  broken commit pass. Building the pushed tip in a temporary worktree would fix this at the cost of a cold build on
  every push. Source: adversarial-architecture finding 7.
- **Docs-only pushes skip the gate:** the hooks run only when a path in `.githooks/code-paths` changes, so
  documentation is reviewed by `/quality-gate full`. Source: adversarial-architecture finding 8.
