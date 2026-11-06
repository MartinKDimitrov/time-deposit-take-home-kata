# Verify findings closed - fix commits 6951c57 and 7178178

Range `c2dec64..a2f1fda`. Mode: push (reading only, nothing was built or run). Every verdict below is from reading the
diff and the findings, so each is SUSPECTED until `/quality-gate full` runs `make test` and the mutations.
Findings sources: `docs/ai-reviews/{adversarial-architecture,adversarial-code,test-quality}.md` and the previous
`verify-findings-closed.md` (items 3b, 2b).

## Not closed (PARTIAL / STILL OPEN)

| Item                | Description                                                            | Status     |
| ------------------- | ---------------------------------------------------------------------- | ---------- |
| arch 9              | Duplicate plan type silent; injection drops defaults; CLAUDE.md silent | PARTIAL    |
| arch 10             | Layering unenforced; public `INTEREST_FREE_DAYS`                       | PARTIAL    |
| arch 6              | Public policies; mutable deposit passed in                             | PARTIAL    |
| test-quality 4      | "Round the interest, not the sum" invariant has no explicit row        | STILL OPEN |
| test-quality 6      | Duplicate `planType` untested                                          | STILL OPEN |
| test-quality 8      | No degenerate inputs (empty list, zero, negative, days 0)              | STILL OPEN |
| test-quality 5      | Plan-type matching: only `Basic` added                                 | PARTIAL    |
| test-quality 7      | `premium, 30` row missing; duplicate rows kept                         | PARTIAL    |
| test-quality 9      | Injected stub still returns constant `10.0`                            | PARTIAL    |

### arch 9 - PARTIAL
- **Done**: `TimeDepositCalculator.kt:15` KDoc says "the last policy for a plan type wins". Rejecting duplicates was not
  done; that is acceptable under hard constraint 5 if recorded.
- **Not done**: `CLAUDE.md:53-54` is unchanged. It still does not say that injection replaces the defaults (so it must
  include `defaultPolicies()`), and the `defaultPolicies()` exception to "wiring in `config/` only" (line 49) is not noted.
  `TimeDepositCalculator(listOf(vip))` still leaves `basic` at 1000.0 with no signal.
- **Overclaim**: commit 6951c57 says "the duplicate plan type rule is documented"; true for the KDoc only.
- **Not pinned**: no test injects a `basic` duplicate, so first-wins would stay green (same as the previous round, item 3b).

### arch 10 - PARTIAL
- `INTEREST_FREE_DAYS` is now `private` (`TimeDepositCalculator.kt:42`); read-confirmed, nothing else referenced it.
- The layering test was not added. The finding tied it to the first adapter landing; record that deferral.

### arch 6 - PARTIAL
- The three policies are `internal` (the finding's first action). Nothing outside `domain/interest` and the calculator
  uses them, and `defaultPolicies()` returns `InterestPolicy`, so it still compiles by reading.
  Effect: previous item 2b is closed from outside the module, still callable inside it.
- Unchanged: policies still receive the mutable `TimeDeposit`; reading one plan still takes two files.

### test-quality 4 - STILL OPEN
- The 6.00 row remains the only guard for "round the addend", and its comment was reworded to "smallest balance that
  earns a cent", which hides that role. The suggested `basic, 1000.004, 31` row was not added.
- The commit message says "all seven surviving mutants are caught". The round-the-sum mutant is not shown to be
  caught by any new row (for example 150.00 + 0.125 gives 150.125, which HALF_UP rounds to 150.13 either way).
  Check in `full`: apply `balance = roundToCents(balance + interest)` and run `make test T=TimeDepositCalculatorTest`.

### test-quality 5 / 7 / 9 - PARTIAL
- 5: `Basic` added; empty string and padded `" basic "` not.
- 7: day-30 injected-plan test added; `premium, 1000.00, 30, 1000.00` not added; rows 1 and 4 (same branch) kept.
- 9: day-30 case added; the stub still returns `10.0`, so rounding of an injected plan is untested.

## Closed

| Item                | Description                                                        | Status |
| ------------------- | ------------------------------------------------------------------ | ------ |
| adversarial-code 1  | Rounding mode, `BigDecimal(Double)`, arithmetic order unpinned     | CLOSED |
| adversarial-code 2  | Balance read before the policy call (order changed by refactor)    | CLOSED |
| test-quality 1-3    | HALF_UP tie, `BigDecimal.valueOf`, `balance * rate / 12` order     | CLOSED |
| verify item 2b      | Direct policy call pays inside the interest-free period           | CLOSED |
| verify item 3       | Day-30 floor not tested for injected plans                         | CLOSED |

- adversarial-code 1: the four rows added (`150.13`, `18.01`, `10.02`, `3.62`) are exactly the rows that report proved
  kill the five survivors; the wrong comment is replaced. Not run here.
- adversarial-code 2: `TimeDepositCalculator.kt:24-25` computes `interest` first, then adds. No test pins the order
  (a policy that writes the balance); a revert would stay green.
- test-quality 1-3: covered by the same rows (the `basic 18.00` row stands in for `student 402.00`).
- verify item 3: the `vip` day-30 test is real and goes through `updateBalance` with the injected policy.

## Happy path and over-correction (by reading)
- No-arg constructor, `defaultPolicies()` and `updateBalance` signature are unchanged; hard constraint 2 holds
  (the interest/balance split keeps the original evaluation order).
- `internal` on the policies does not affect the public API; the test source set sees `InterestPolicy` (still public).

## New defects found while checking
- None beyond the above. Pre-existing: the test file has no newline at end of file.

## Not done
- `make test` and the mutations were not run (push mode). Prove in `/quality-gate full`.
