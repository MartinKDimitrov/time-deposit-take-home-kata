# Regression hunt: InterestPolicy strategy extraction

Range reviewed: `7d6fc90..8404993` (commits `4a55d7b` characterization tests, `8404993` refactor).
The task brief named `origin/main..HEAD`, but `origin/main` already points at `7d6fc90`; the refactor
commits are the two unpushed commits above. Reviewed those.

## Verdict

**Regression found: NO.**

The extraction of the nested if/else interest ladder into `InterestPolicy` strategies is
behaviour-preserving. Verified by exhaustive differential execution against a byte-for-byte replica of
the original implementation.

## What the change was trying to achieve

Replace the hard-coded nested `if (days>30) { student/premium/basic ... }` ladder in
`TimeDepositCalculator.updateBalance` with one `InterestPolicy` per plan type, looked up by `planType`,
while keeping the public API (no-arg constructor, `updateBalance(List<TimeDeposit>)`), the `Double`
arithmetic, and the `BigDecimal(Double)` + `HALF_UP` rounding identical.

## How behaviour maps, old -> new

| Original nested-if | Refactored | Equivalent? |
|---|---|---|
| `days > 30` global guard, else 0 | `if (deposit.days <= INTEREST_FREE_DAYS /*30*/) return 0.0` applied before lookup | yes |
| unknown/other plan -> 0 | `policiesByPlanType[planType] ?: 0.0` | yes |
| basic -> `balance*0.01/12` always | `BasicInterestPolicy` ANNUAL_RATE 0.01, no day condition | yes |
| student, `days < 366` -> `balance*0.03/12` | `StudentInterestPolicy`, `days <= 365` (LAST_DAY_WITH_INTEREST), rate 0.03 | yes (`<=365` == `<366`) |
| premium, `days > 45` -> `balance*0.05/12` | `PremiumInterestPolicy`, `days > 45` (INTEREST_STARTS_AFTER_DAYS), rate 0.05 | yes |
| `balance * rate / 12` | `monthlyShare(balance, rate) = balance * rate / 12` | yes, same operand order |
| `BigDecimal(interest).setScale(2, HALF_UP).toDouble()` | `roundToCents` identical | yes |

`defaultPolicies()` registers exactly `BasicInterestPolicy()`, `StudentInterestPolicy()`,
`PremiumInterestPolicy()` with rates 0.01 / 0.03 / 0.05 - the same three plans and rates as the original.

## Evidence (CONFIRMED)

Built and ran the suite in a detached worktree at `8404993` with the project's own toolchain
(`make test`). All 14 characterization tests pass.

Added a temporary differential test (`DiffHarnessTest`, since removed) that, for each case, ran a local
copy of the original nested-if logic and the real compiled `TimeDepositCalculator().updateBalance`
against the same `TimeDeposit`, then compared the resulting `balance` with exact `Double` equality.

Grid exercised:
- plans: `student, premium, basic, BASIC, Student, savings, "", corporate, PREMIUM`
  (includes unknown plans and case variants - matching is case-sensitive `String` equality in both)
- balances: every integer 0..20000 plus rounding-stress values
  (`0.004, 0.01, 1234.567, 999999.99, 1e9, 0.5, 2.5, 11999.99, 7.0/3.0, ...`)
- days: `0,1,29,30,31,44,45,46,364,365,366,367,100,730` - every stated boundary

Result:

```
DIFF checked=2521764 mismatches=0
```

Over 2.52 million cases, zero divergences. Boundaries 30/31, 45/46, 365/366 all match exactly, as do
the unknown-plan and HALF_UP-rounding paths.

## Notes checked, not regressions

- The global `INTEREST_FREE_DAYS = 30` guard runs before policy lookup, so a custom policy also earns
  nothing for `days <= 30`. This mirrors the original global `days > 30` guard, so it is consistent, not
  a change. The `vip` test uses `days = 31`, above the threshold.
- `associateBy { it.planType }` would silently keep the last entry on a duplicate `planType`;
  `defaultPolicies()` has no duplicates, so no effect on current behaviour.
- `make verify` (Testcontainers IT) not run - the change touches only the pure-domain calculator, which
  the unit suite and the differential grid cover; IT exercises the HTTP/persistence layer, unchanged here.

## Findings

None. No happy-path break, no ineffective fix, no load/rare-input regression.
