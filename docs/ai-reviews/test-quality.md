# test-quality — 61783bc..c2dec64 (push mode)

Scope: `TimeDepositCalculator.kt`, `domain/interest/*.kt`, `TimeDepositCalculatorTest.kt`.
`TimeDepositCalculatorTest` is the **only** test file in the repository at this commit, and `src/main` contains only
the six files above — nothing else backs up hard constraints 1 and 2. No coverage tool is configured in `pom.xml`
(no JaCoCo/Kover), so coverage blind spots below come from reading, not from a report.

## Mutation results

Hand mutations applied in a detached worktree (`make test T=TimeDepositCalculatorTest`, two runs, 14 tests each).
Row numbers are the `@CsvSource` rows of `TimeDepositCalculatorTest.kt:16-27`.

| # | Function | Mutation | Caught |
| --- | --- | --- | --- |
| 1 | `TimeDepositCalculator.monthlyInterest` (30-day floor) | `days <= INTEREST_FREE_DAYS` -> `days <` | **yes** — rows 1, 4 (`1000.0` vs `1000.83` / `1002.5`) |
| 2 | `StudentInterestPolicy.monthlyInterest` | `days <= 365` -> `days < 365` | **yes** — row 6 (`1002.5` vs `1000.0`) |
| 3 | `PremiumInterestPolicy.monthlyInterest` | `days > 45` -> `days >= 45` | **yes** — row 9 (`1000.0` vs `1004.17`) |
| 4 | `TimeDepositCalculator.monthlyInterest` (unknown-plan fallback) | `?: 0.0` -> `?: BasicInterestPolicy().monthlyInterest(deposit)` | **yes** — row 11 |
| 5 | `TimeDepositCalculator.updateBalance` (what gets rounded) | `balance += round(interest)` -> `balance = round(balance + interest)` | **yes, by one row only** — row 12; rows 1-11 and both `@Test` cases pass |
| 6 | `TimeDepositCalculator.monthlyInterest` (plan lookup) | `planType` -> `planType.lowercase()` | **NO — survives** |
| 7 | `roundToCents` | `BigDecimal(amount)` -> `BigDecimal.valueOf(amount)` | **NO** (reported by adversarial-code; consistent with the row analysis below) |
| 8 | `roundToCents` | `HALF_UP` -> `HALF_EVEN` | **NO** (same source; no row contains a tie — proven below) |
| 9 | `monthlyShare` | `balance * rate / 12` -> `balance / 12 * rate` | **NO** (same source) |

Verdict: the **day boundaries and the plan ladder are genuinely pinned**; the **exact-arithmetic half of hard
constraint 2 (`Double`, `BigDecimal(Double)`, `HALF_UP`) is not pinned at all**, and the one row that was written to
cover it does not do so.

### 1. High — PROVEN: no row contains a rounding tie, so `HALF_UP` is unpinned and row 12's comment is wrong

- **Where** `TimeDepositCalculatorTest.kt:27` (`"basic, 6.00, 31, 6.01" // 0.005 is rounded HALF_UP`); `TimeDepositCalculator.kt:35`.
- **Problem** `6.0 * 0.01 / 12` is the double whose exact value is `0.005000000000000000104083408558608425664715468883514404296875` — strictly *above* the tie. No tie-break ever runs: `HALF_UP`, `HALF_EVEN`, `UP` and `CEILING` all produce `0.01`. The row pins "do not truncate", nothing more. Row 10 (`premium 46 -> 1004.17`, interest `4.1666…`) already pins that.
- **Why it matters** `HALF_UP` is named verbatim in hard constraint 2 and is the rule most likely to be "tidied" to `HALF_EVEN` (banker's rounding) by a later contributor. The suite would stay green.
- **Action** Add a row with an exactly representable tie: `premium, 30.00, 46, 30.13` (interest is exactly `0.125`; `HALF_EVEN` gives `30.12`). `student, 50.00, 31, 50.13` is the same tie via another plan. Then fix or delete the misleading comment.

### 2. High — PROVEN by construction: `BigDecimal(Double)` vs `BigDecimal.valueOf` is unpinned

- **Where** `TimeDepositCalculator.kt:34-35`; no row in `TimeDepositCalculatorTest.kt:14-28` exercises the difference.
- **Problem** The two constructors differ only when the double's exact binary expansion falls on the other side of the tie from its shortest decimal form. Every balance in the table is far from such a value, so mutation 7 survives despite the comment "BigDecimal(Double) is intentional".
- **Why it matters** The comment asserts an invariant that no test enforces; `valueOf` is the idiomatic replacement a linter or reviewer will suggest, and it silently changes money.
- **Action** Add `student, 402.00, 31, 403.00`. `402.0 * 0.03 / 12` is the double `1.005`, whose exact value is `1.00499999999999989341858963598497211933135986328125`: `BigDecimal(Double)` + `HALF_UP` yields `1.00`, `BigDecimal.valueOf(1.005)` yields `1.01`.

### 3. High — PROVEN by construction: the `balance * rate / 12` order is unpinned

- **Where** `domain/interest/InterestPolicy.kt:22-23` (`monthlyShare`), whose KDoc claims the order is preserved "so results stay identical".
- **Problem** All table balances are order-insensitive at cent precision (checked `1234567.00` too), so the reassociated mutant survives. The claim in the KDoc is untested.
- **Why it matters** `monthlyShare` is the single arithmetic kernel for all three plans; reordering it is a one-character refactor that changes results for small balances.
- **Action** Add `student, 10.00, 31, 10.02` (`10*0.03/12` -> `0.02`, `10/12*0.03` -> `0.03`) or `student, 2.00, 31, 2.01` (`0.01` vs `0.00`). These rows also give the table its first sub-cent-magnitude interest.

### 4. Medium — PROVEN: the "round the interest, not the balance" invariant hangs on one row, for undocumented reasons

- **Where** `TimeDepositCalculator.kt:23`; caught only by `TimeDepositCalculatorTest.kt:27`.
- **Problem** Mutating to `balance = roundToCents(balance + monthlyInterest(deposit))` keeps rows 1-11 and both `@Test` cases green; only the `6.00 -> 6.01` row fails. That row is labelled as a `HALF_UP` case (finding 1), so anyone "fixing" the wrong comment is likely to delete the suite's only guard on where rounding happens.
- **Why it matters** Rounding the sum instead of the addend is exactly the kind of plausible simplification a refactor makes, and it diverges from the original for any balance with more than two decimals or any balance large enough to lose precision in the addition.
- **Action** Add an explicit row with a sub-cent balance, e.g. `basic, 1000.004, 31` (original: `1000.834`; round-the-sum: `1000.83`), named for that invariant rather than relying on row 12.

### 5. Medium — PROVEN: plan-type matching is pinned by one literal only

- **Where** `TimeDepositCalculator.kt:30`; `TimeDepositCalculatorTest.kt:26` (the single `unknown` row).
- **Problem** Replacing the lookup key with `deposit.planType.lowercase()` survives the whole suite. No row uses `"Basic"`, `"BASIC"`, `""`, a padded `" basic "`, or a plan name that is a prefix of a known one. The original code compared with `==` on the raw string; nothing enforces that.
- **Why it matters** `planType` arrives from persistence/HTTP as a free string. Normalisation added "helpfully" in the adapter or here changes who earns interest, and the characterization table will not notice.
- **Action** Add rows `Basic, 1000.00, 31, 1000.00` and `"", 1000.00, 31, 1000.00` (empty string via `@CsvSource(nullValues/emptyValue)` or a separate `@Test`) to pin case-sensitive exact matching.

### 6. Medium — SUSPECTED: duplicate `planType` in the policy list resolves silently and is untested

- **Where** `TimeDepositCalculator.kt:19` (`policies.associateBy { it.planType }`); `TimeDepositCalculatorTest.kt:57-69`.
- **Problem** `associateBy` keeps the last entry for a duplicate key, so an injected policy can silently shadow `BasicInterestPolicy` with no error. The extensibility test only adds a *fresh* key (`vip`), so it proves the happy path of injection and nothing about collisions or about `defaultPolicies()` being complete.
- **Why it matters** Constructor injection is the documented extension point; a shadowing override is either the intended feature (then pin it) or a silent misconfiguration (then it should fail loudly).
- **Action** Add a test injecting a policy with `planType = "basic"` and assert the chosen semantics. Check that proves it: run that test against the current code and see which rate applies.

### 7. Medium — Several parametrised rows are the same case; the premium floor row is weaker than it looks

- **Where** `TimeDepositCalculatorTest.kt:16-27`.
- **Problem** Rows 1 and 4 (`basic 30`, `student 30`) exercise the *same single* `if` at `TimeDepositCalculator.kt:28` — the floor is now plan-independent, so they are one case twice. Row 3 (`basic 1234567.00, 45`) adds magnitude but no new branch or rounding behaviour over row 2. Row 8 (`premium, 31, 1000.00`) cannot distinguish the global 30-day floor from premium's own 45-day rule, and there is no `premium, 30` row at all.
- **Why it matters** A 12-row table reads as 12 independent guards; mutation 1 shows one branch covered twice while the interaction of the two premium thresholds is covered zero times.
- **Action** Keep one day-30 row, add `premium, 1000.00, 30, 1000.00`, and add a `vip, 1000.00, 30, 1000.00` case to the injected-policy test to pin that the calculator (not the policy) owns the floor for new plans.

### 8. Low — No degenerate or negative inputs anywhere

- **Where** `TimeDepositCalculatorTest.kt` (whole file).
- **Problem** No empty list, no zero balance, no negative balance, no `days = 0` or negative `days`, no multi-element list larger than two. `HALF_UP` on a negative interest rounds away from zero — real original behaviour that is uncharacterized.
- **Why it matters** `updateBalance` is called over whatever the repository returns; characterization tests exist precisely to freeze the odd corners, and these are the corners a rewrite would get wrong without noticing.
- **Action** Add `basic, 0.00, 31, 0.00`, a negative-balance row, and `updateBalance(emptyList())` as a no-throw/no-op case.

### 9. Low — The injected-policy test asserts mostly its own stub

- **Where** `TimeDepositCalculatorTest.kt:57-69`.
- **Problem** The anonymous policy returns the constant `10.0`, so `1010.0` only proves "the map was consulted and the result added". It does not exercise `monthlyShare`, the floor, or rounding of an injected plan.
- **Why it matters** It is a wiring test labelled as plan support; useful, but it should not be read as covering new-plan behaviour.
- **Action** Make the stub return a value needing rounding (e.g. `0.125`) and add the `days = 30` case from finding 7.

## What is genuinely strong

- The day boundaries do what they claim: all four boundary/guard mutations (30/31, 365/366, 45/46, unknown plan) were
  caught, each by the specific row written for it, with the expected failure message. Rows 1, 4, 6, 9 and 11 are real
  assertions on real branches.
- The plan ladder (`basic` 1%, `student` 3%, `premium` 5%) is pinned by exact expected balances, not by recomputation
  from the implementation; no fixture is built by calling the code under test.
- Hard constraint 2's *interface* is pinned structurally: `TimeDepositCalculator()` at line 11 keeps the no-arg
  constructor compiling, and `updateBalance(listOf(deposit))` plus in-place mutation assertions keep the signature and
  the mutate-the-argument contract. The `containsExactly` test correctly pins that `id`, `planType` and `days` are
  untouched (hard constraint 1's shape, as far as a test can).
- The table is behaviour-oriented, not implementation-coupled: it survives the policy extraction itself, which is the
  point of a characterization suite.
