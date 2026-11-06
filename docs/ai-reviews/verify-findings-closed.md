# Verify findings closed - adversarial-architecture findings 1-3

Range: `61783bc..c2dec64`. Fix commit: `c2dec64`. Findings source: `.git/ai-reviews-previous/adversarial-architecture.md`.

## Verdict

**The commit message's claim "Addresses adversarial-architecture findings 1-3" does not fully hold.** Finding 1 is
closed. Finding 2 is closed as a documentation gap, but its trigger input behaves exactly as before. Finding 3 is half
done: the duplicate `planType` collision was not touched, in code, in docs or in tests.

`c2dec64` changes comments and `CLAUDE.md` only, so no behaviour changed and nothing was enforced or tested by it.

| Item | Description                                          | Status     |
| ---- | ---------------------------------------------------- | ---------- |
| 3    | Registration claim and its caveats (rollup)          | PARTIAL    |
| 3b   | Duplicate `planType` collides silently, undocumented | STILL OPEN |
| 3a   | Claim omitted constructor injection                  | CLOSED     |
| 2    | 30-day floor split from the policies (rollup)        | PARTIAL    |
| 2b   | Direct policy call pays interest on day 1            | STILL OPEN |
| 2a   | Interface docstring silent on who owns the floor     | CLOSED     |
| 1    | `TimeDeposit` is a domain type in the root package   | CLOSED     |

The finding 2 rollup is a judgement call. The finding complained about the missing statement and did not ask for
enforcement, so if a doc-only resolution is accepted, finding 2 is closed. The hazard itself is unchanged.

## Open and partial items

### 1. Low — Finding 3b: duplicate planType still collides silently and is still undocumented

- **Where**: `src/main/kotlin/org/ikigaidigital/TimeDepositCalculator.kt:19`; `CLAUDE.md:53-55` is silent on it.
- **Problem**: Balance 1000.0, 31 days, plan `basic`, through `updateBalance`. `defaultPolicies() + dupBasic(10.0)` gives 1010.0; `listOf(dupBasic(10.0)) + defaultPolicies()` gives 1000.83, the injected policy ignored with no error; `listOf(vip(10.0), vip(20.0))` gives 1020.0 on plan `vip`. A search of `CLAUDE.md`, `src` and `docs/ASSIGNMENT.md` finds no mention of the behaviour.
- **Why it matters**: The finding called this "an undocumented edge"; it remains one. The new `CLAUDE.md` text recommends constructor injection, the only path where a collision can occur, and list order silently decides the rate. Mutating `associateBy` to first-wins still passes all 14 tests, so nothing pins it. The default no-arg path is unaffected: its three plan types are distinct.
- **Action**: Add one sentence to `CLAUDE.md:53-55` or the `TimeDepositCalculator` KDoc: the last policy for a `planType` wins, silently. Hard constraint 5 means a rejecting guard should not be added unasked.

### 2. Low — Finding 2b: direct policy call still pays inside the interest-free period

- **Where**: `src/main/kotlin/org/ikigaidigital/domain/interest/BasicInterestPolicy.kt:8-9`, `StudentInterestPolicy.kt:8-9`, `InterestPolicy.kt:8-10`.
- **Problem**: `BasicInterestPolicy().monthlyInterest(TimeDeposit(1, "basic", 1000.0, 1))` returns 0.8333333333333334, the same as before the fix. `StudentInterestPolicy` returns 2.5 for the same case; the finding named only Basic. `PremiumInterestPolicy` returns 0.0 because of its own 45-day rule.
- **Why it matters**: The fix is a disclaimer ("not meant to be called directly"); the classes stay public and nothing enforces it. Exposure is limited: `TimeDepositCalculator` is the only production caller and it applies the floor correctly.
- **Action**: Accept this as the documented design and record that decision, or restrict visibility in a separate change. Do not describe it as fixed in code.

## New defects found while checking

### 3. Low — The new "applies to every plan" rule is not tested for injected plans

- **Where**: `CLAUDE.md:53-55`; `src/test/kotlin/org/ikigaidigital/TimeDepositCalculatorTest.kt:57-69`.
- **Problem**: The injected-plan test uses only day 31. Mutation: remove the calculator guard and put a `days > 30` check in Basic and Student instead. All 14 tests pass, yet injected plans would lose the floor.
- **Why it matters**: The current code is correct: an injected policy gives 1000.0 at days 1 and 30, was asked 0 times, and gives 1010.0 at day 31. But the rule `c2dec64` documents can regress with a green build, on the injection path it recommends. Deleting the guard outright is caught (2 failures).
- **Action**: Add a day-30 assertion for an injected plan to the existing test.

### 4. Low — CLAUDE.md overstates what hard constraint 2 pins

- **Where**: `CLAUDE.md:51`, against `CLAUDE.md:22-25` and `docs/ASSIGNMENT.md:42,56`.
- **Problem**: Line 51 says both classes are "pinned to the root package by hard constraints 1 and 2". Only constraint 1 names a package, and only for `TimeDeposit`. Constraint 2 covers signature, behaviour and the no-arg constructor. The assignment pins no package for the calculator.
- **Why it matters**: The new sentence cites a rule that does not say this. The error came from the original finding's wording and was carried into the project rules, which later reviews treat as authoritative.
- **Action**: Add the package to constraint 2, or reword line 51 to say the calculator stays there by choice.

## Closed items

- **Finding 1**: `CLAUDE.md:51-52` carries the sentence the finding asked for and extends it to `TimeDepositCalculator`, apart from the citation in item 4. The import graph is unchanged and acyclic, with no framework imports under `domain/`. The upward package edge remains, as the finding accepted.
- **Finding 2a**: `InterestPolicy.kt:8-10` and `CLAUDE.md:54-55` now say the calculator owns the floor. Verified through `updateBalance`: basic, student, premium and an injected plan all give 1000.0 at days 1 and 30, and the policy is not called.
- **Finding 3a**: `CLAUDE.md:53-54` names both routes, `defaultPolicies()` and constructor injection.

## Valid-case and regression checks

- **Happy path**: no-arg constructor, premium, 1000.0, day 46 gives 1004.17.
- **Valid injection**: `defaultPolicies() + vip(10.0)` gives 1010.0 on `vip` and leaves `basic` at 1000.83.
- **Hard constraint 2**: 200,000 randomised cases, including boundary days, negative balances and unknown plans, are bit-identical to the original calculator from `61783bc`.
- **Over-correction**: none possible, since `c2dec64` adds no guard.

## Checks run

- `make test` at `c2dec64` in a private worktree: passed, 14 tests, 0 failures, confirmed from the Surefire report.
- Reproductions: a scratch `AuditReproTest`, 5 tests passed, run through `make test T=AuditReproTest`.
- Mutations, each against `TimeDepositCalculatorTest`: guard deleted (2 failures); guard moved into Basic and Student (passed); first-wins (passed); `require` on duplicates (passed).
- `make verify` was **not run**: Docker is unavailable and the task excluded it. The range contains no `*IT` tests.
- The worktree was removed. Only this report was written to the main checkout.
