# CLAUDE.md – project rules for AI assistants

This file is read by Claude (Claude Code / Claude desktop agent) at the start of every session.
It holds the rules the AI must follow when generating, reviewing or refactoring code in this repository.

## Context

XA Bank time deposit service (take-home kata). Kotlin 2.3, Ktor 3 (Netty), Exposed 1.x, Java 17, Maven wrapper,
PostgreSQL + Flyway, OpenAPI contract-first, Testcontainers. The original task is in `docs/ASSIGNMENT.md`.

## Commands

All build and test commands go through `make`; agents use only these targets (`make help` lists them).

- `make test` – compile + unit tests `*Test`, no Docker, seconds; one class: `make test T=<TestClass>`
- `make verify` – unit + integration tests `*IT` with Testcontainers; Docker required
- `make run` – start the app with demo data (humans only; agents never start the app)
- `make setup` – enable the git hooks once per clone

## Hard constraints (never break these)

1. `TimeDeposit` (`src/main/kotlin/org/ikigaidigital/TimeDeposit.kt`) is a shared class: do not change its
   fields, types, package or constructor.
2. `TimeDepositCalculator.updateBalance(xs: List<TimeDeposit>)` keeps its signature and its exact behaviour,
   including `Double` arithmetic and `BigDecimal(Double)` + `HALF_UP` rounding. The no-arg constructor must keep working.
3. Exactly two HTTP endpoints: `GET /time-deposits` and `POST /time-deposits/balance-updates`. No others.
4. The API is defined in `src/main/resources/openapi/time-deposits.yaml`. Change the contract first, then routes and
   DTOs; the integration tests validate every response against the contract, so the two cannot drift apart.
5. Invalid input / exception handling is out of scope (per the assignment) – do not add it unasked.

## Architecture (hexagonal, single module)

```
org.ikigaidigital
  TimeDeposit, TimeDepositCalculator   shared domain classes (original package kept)
  domain/                              pure Kotlin: Withdrawal, Money, interest/*Policy
  application/port/inbound             use case interfaces
  application/port/outbound            repository interfaces
  application/service                  use case implementations
  adapter/web                          Ktor routes, response DTOs, mappers
  adapter/persistence                  Exposed tables, port implementations, Hikari pool + Flyway migrations
  config/                              composition root: the only place that wires adapters to use cases
```

- Dependencies point inwards only: adapters -> application -> domain. Domain and application are plain Kotlin:
  no Ktor, Exposed or serialization imports.
- Transactions go through the `TransactionRunner` outbound port; blocking database work runs on `Dispatchers.IO`,
  decided in the web adapter.
- Exposed tables and DTOs never leave their adapter; map at the boundary. Wiring is manual, in `config/` only.
- Money outside the domain is `BigDecimal` with scale 2; convert with `Double.toMoney()` only.
- A new interest plan = a new `InterestPolicy` implementation registered in `defaultPolicies()`.

## Code style

- Kotlin official style, 4 spaces, trailing commas, small functions, expressive names (no `xs`, `a2d`).
- No magic numbers: named constants next to the rule they belong to.
- Comments explain *why* (assumptions, constraints), not *what*.
- No unused code, no speculative abstractions, no new dependencies without a stated reason.

## Tests

- Characterization tests protect `updateBalance`; they must stay green on every change.
- Prefer real collaborators over mocks. Domain = plain unit tests. Integration = Ktor `testApplication` + Testcontainers
  PostgreSQL; integration tests always run against real PostgreSQL, never an in-memory substitute.
- One test per behaviour; cover boundaries (30/31, 45/46, 365/366 days), not every permutation.
- Unit tests are named `*Test` (Surefire, `test` phase); integration tests are named `*IT` (Failsafe, `verify` phase).

## Workflow

- Work in small steps; one logical change per commit, build green at every commit.
- Conventional Commits: `feat:`, `fix:`, `refactor:`, `test:`, `build:`, `docs:`, `chore:`.
- Quality gates (git hooks live in `.githooks/`; enable once per clone with `make setup`):

  | Moment | Automatic (git hook) | AI, triggered by the human |
  | --- | --- | --- |
  | `git commit` | `pre-commit`: `make test`; skipped when no code or build file is staged | `/review-step` on the change being committed |
  | `git push` | `pre-push`: `make verify`, then the AI gate (`claude -p "/quality-gate auto <range>"`); skipped when the pushed commits touch no code or build file | review the reports, fix or record the findings, commit the reports, push again |
  | handover / release | – | `/quality-gate full` on the whole range |

- Push when a slice is complete and green, not after every commit: the push is where the expensive checks run.
- When the AI gate writes reports the push stops. Commit the reports (and any `fix:` commits) and push again;
  only changes not yet reviewed are reviewed again. Review agent reports are committed with the slice.
- Without Claude Code or a connection, the hook says what will not run and asks whether to continue.
  `SKIP_AI_GATE=1 git push` skips only the AI review; `git push --no-verify` skips every check.
- When something is ambiguous, pick the most reasonable reading, implement it, and record it in
  `docs/TRADEOFFS.md` (Assumptions section) and/or a code comment.
- The human author writes and reviews all production code; the AI proposes, verifies and documents.
