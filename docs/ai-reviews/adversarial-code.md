# Adversarial code review: domain refactoring (push mode)

Range: `c2dec64..a2f1fda`. Scope: `TimeDepositCalculator.kt`, `domain/interest/*Policy.kt`. Read-only; nothing was built or run.

## Verdict

**PASS.** No user-reachable defect. The production diff is behaviour-neutral: three `class` -> `internal class`,
`INTEREST_FREE_DAYS` public -> private, one local `val` extracted, one KDoc line.

## Findings

### 1. Low — SUSPECTED: duplicate plan types are silently dropped (documented, not guarded)

- **Where:** `TimeDepositCalculator.kt:19` (`policies.associateBy { it.planType }`), KDoc line 15.
- **Problem:** two injected policies with the same `planType` leave one of them dead with no error. The new KDoc states "last wins", but nothing enforces or tests it here.
- **Why it matters:** latent only; `defaultPolicies()` has unique types, and the constructor is not reachable from HTTP. Bites when someone adds a plan and copies a `planType` string.
- **Action:** in `full` mode check `TimeDepositCalculatorTest` for a test pinning "last wins". Otherwise leave as is (invalid-input handling is out of scope per the constraints).

### 2. Low — SUSPECTED: public `InterestPolicy.monthlyInterest` skips the 30-day guard if called directly

- **Where:** `InterestPolicy.kt:5-13` (KDoc only), `BasicInterestPolicy.kt` (no day check).
- **Problem:** the interest-free period lives only in the calculator. `internal` on the three implementations now stops outside callers getting `BasicInterestPolicy`, but a custom or test policy can still be called directly. That is covered only by a KDoc sentence.
- **Why it matters:** latent; there is no caller today. It bites if a future component (for example a projection endpoint) calls policies without the calculator.
- **Action:** none now. If such a caller is added, move the guard behind one shared entry point.

## Checked and found sound

- `deposit.balance += interest` is `set(get() + interest)`. `monthlyInterest` does not mutate the deposit, so extracting the `val` changes no order of evaluation.
- Making `INTEREST_FREE_DAYS` private breaks nothing: the only reference in `src` is `TimeDepositCalculator.kt:31`.
- `internal` policies are used from `defaultPolicies()` (public, return type `List<InterestPolicy>`, a public interface). That is legal and leaks no internal type. Tests in the same module still see them.
- Boundaries: calculator `days <= 30` gives 0; premium `> 45`; student `<= 365`. These match the original (31, 46, and 365/366 as the first day without interest) and are unchanged by this diff.
- Unknown or differently-cased `planType` earns 0, the same as the original.
- Hard constraints 1 and 2 hold: `TimeDeposit` is untouched, and `updateBalance` keeps its signature, the `BigDecimal(Double)` + `HALF_UP` rounding and the `balance * rate / 12` order.
- Last round's Medium (rounding mode and arithmetic order unprotected) was addressed by the test commit `7178178`. Not re-proven here; `verify-findings-closed` owns that.
