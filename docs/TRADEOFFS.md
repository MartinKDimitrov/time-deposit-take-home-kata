# Trade-offs and assumptions

Decisions taken while solving the kata, with the reason and what was given up. Findings from the AI review
agents (`docs/ai-reviews/`) that were accepted rather than fixed are recorded here as well, with their source.

## Assumptions

| # | Assumption | Why |
| --- | --- | --- |
| A1 | The interest-free first 30 days apply to every plan, including plans added later. | The assignment says "for any existing plans"; a uniform rule is the simpler reading, and a plan that should pay earlier can be supported by moving the rule into the policies. Source: adversarial-architecture finding 4. |
| A2 | Plan types are matched case-sensitively, and an unknown plan type earns no interest. | This is what the original `updateBalance` does; hard constraint 2 keeps that behaviour. A test pins both. |
| A3 | "No interest after 1 year" means the student plan pays while `days <= 365`. | The original code pays while `days < 366`. |

## Domain

### Money stays `Double` inside `TimeDeposit` and the calculator

`TimeDeposit.balance` is a `Double` and the class is shared, so it cannot change. The calculator keeps the
original arithmetic (`balance * rate / 12`) and the original rounding (`BigDecimal(Double)` with `HALF_UP`),
because hard constraint 2 requires exactly the same results. `BigDecimal.valueOf` would be the textbook choice,
but it changes real balances (18.00 gets 18.02 instead of 18.01), so the characterization tests pin
`BigDecimal(Double)` on purpose.

Outside the shared class, money is `BigDecimal` with scale 2 (`NUMERIC(19,2)` in the database). Long cents were
considered and rejected: every value would have to be converted from and to the `Double` the shared class
requires, which adds a second rounding step without removing the first.

### Interest rules as strategies

Each plan is an `InterestPolicy`; the calculator looks the policy up by plan type. A new plan is a new class,
added to `defaultPolicies()` or injected through the constructor. Accepted consequences:

- **Duplicate plan types:** the last policy in the list wins, silently. This can only happen through constructor
  injection; it is documented in the calculator's KDoc. No guard was added, because rejecting invalid input is
  out of scope (hard constraint 5). Sources: adversarial-architecture finding 9, verify-findings-closed item 1,
  test-quality (push review).
- **Injection replaces the defaults:** `TimeDepositCalculator(listOf(vip))` drops the three built-in plans. Callers
  pass `defaultPolicies() + vip`, as the extensibility test does. Source: adversarial-architecture finding 9.
- **The 30-day rule lives in the calculator, not in the policies:** policies are `internal`, so the only way to
  ask one is through the calculator, which applies the rule first. Source: adversarial-architecture finding 6.
- **Policies receive the mutable `TimeDeposit`:** passing only `balance` and `days` would be cleaner, but would
  widen the change for no current benefit. The calculator computes the interest before it reads the balance, as
  the original did, so a policy cannot lose a balance update. Source: adversarial-code finding 2.

### `TimeDeposit` and `TimeDepositCalculator` stay in the root package

Both are pinned by hard constraints 1 and 2, so they stay in `org.ikigaidigital` and are treated as domain
types. The root package and `domain/interest` therefore reference each other; there is no class-level cycle.
Source: adversarial-architecture findings 1 and 10.

### No layering test yet

The project is a single Maven module, so the compiler cannot stop the domain from importing an adapter
framework. The rule is held by review (`/review-step`, the architecture agent). A layering test (ArchUnit or
Konsist) is worth adding once the adapters exist; it was not added for a domain-only codebase. Source:
adversarial-architecture finding 10.

## AI quality gate

### Two levels: a quick gate on push, a thorough review on purpose

The pre-push hook runs `make verify` and then the AI gate in push mode: read-only review agents on Sonnet, in
parallel, stopped after three minutes. Running code to prove a finding (mutation testing, differential tests,
hook experiments), the slow agents (regression, architecture, spec and docs review) and Opus are kept for
`/quality-gate full`, run once before handover. The first version reviewed everything on every push and took
over 15 minutes for a two-class change, mostly model time rather than build time.

What was given up: findings at push time are marked SUSPECTED until `full` proves them.

### The stop after a review is a reminder, not a block

When the gate writes reports the push stops; the identical second push goes through even if a finding is
still open. A hard block would need a machine-readable verdict and a way to override it, and would loop if
the reports themselves were reviewed again. Source: adversarial-architecture finding 2.

### Known limits of the hooks

- **Single branch:** a push of several refs at once is judged by the last ref only. The kata uses `main` only.
  Source: adversarial-architecture finding 3.
- **Working tree, not the pushed commit:** `make verify` builds the working tree. Unstaged changes can make a
  broken commit pass. Building the pushed tip in a temporary worktree would fix this at the cost of a cold
  build on every push. Source: adversarial-architecture finding 7.
- **Docs-only pushes skip the gate:** the hooks run only when a path in `.githooks/code-paths` changes, so
  documentation is reviewed by `/quality-gate full`. Source: adversarial-architecture finding 8.
