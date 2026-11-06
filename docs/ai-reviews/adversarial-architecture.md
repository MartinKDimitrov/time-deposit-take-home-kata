# Adversarial architecture review

- **Range:** `61783bc3de876d011002c4001ccc9800cf3953d3..c2dec64dcaa42e42a438c93e4daa484d0fcf264a`
- **Scope:** `domain/interest/`, `TimeDepositCalculator.kt`, `.githooks/pre-push`, `.gitignore`, against `CLAUDE.md` at the tip
- **Verdict:** **Domain design sound, keep it. Push gate does not do what `CLAUDE.md` says: fix findings 1-3 before relying on it.**
- **Method:** import graph by script (imports plus same-package references, comments stripped). Hook logic run in a throwaway
  repository with stubbed `make`, `claude` and `curl`. Probe tests and one `make verify` run in a private worktree, since removed.
- **Layering tool:** none configured (no ArchUnit, Konsist, detekt or ktlint in `pom.xml`).

## Findings

### 1. High — `make verify` never runs `*IT` tests, yet reports that they passed (CONFIRMED)

- **Where:** `pom.xml:54-57` (Surefire only, no Failsafe); `Makefile:17-19`; `.githooks/pre-push:3,28`; `CLAUDE.md:16,70`
- **Problem:** `CLAUDE.md:70` says `*IT` runs under Failsafe in the `verify` phase, but Failsafe is not in the POM. A deliberately failing `DeliberatelyFailingIT` left `make verify` at exit 0, printing `OK: unit and integration tests passed`, with no `target/failsafe-reports`.
- **Why it matters:** The first integration test added will be silently skipped, including the tests hard constraint 4 relies on to stop contract drift. The pre-push hook would then wave through a failing `*IT`.
- **Action:** Add `maven-failsafe-plugin` (`integration-test` and `verify` goals) before the first `*IT` exists, and confirm a failing `*IT` turns `make verify` red.

### 2. High — A blocking AI review stops one push only; repeating `git push` gets through (CONFIRMED)

- **Where:** `.githooks/pre-push:70-78`; `CLAUDE.md:85`
- **Problem:** `CLAUDE.md:85` says the push stops until reports are committed. The hook records `$tip` as reviewed at line 73, before it looks at the reports, and the only stop signal is an uncommitted change under `docs/ai-reviews`. With a stubbed `VERDICT: BLOCK` report, the first push exited 1 and the identical second push exited 0 (`AI review already done`), the report still untracked.
- **Why it matters:** The gate has no verdict channel, so a critical finding and a clean report behave the same. Signals are also wrong both ways: `claude` exiting 0 with no report gave `found nothing to report` and exit 0, and a stale uncommitted report stopped a push the review had not reported on.
- **Action:** Advance the state only once reports are committed, or have `/quality-gate` write a machine-readable verdict that the hook checks. Otherwise document the stop as a reminder, not a gate.

### 3. High — A multi-ref push checks only the last ref (CONFIRMED)

- **Where:** `.githooks/pre-push:13-20,25`
- **Problem:** The read loop overwrites `base` and `tip` on each ref, so only the last one decides. Pushing a code branch then a docs-only branch exited 0 with no `make verify` and no AI review; the swapped order ran both. `main` with new code plus a tag on an already-pushed commit also exited 0 with nothing run.
- **Why it matters:** `git push --all`, `--follow-tags` or `git push origin main v1.0` skips every check with no message, which contradicts `CLAUDE.md:81`. A release tag push is the moment the gate matters most.
- **Action:** Evaluate each ref inside the loop, or accumulate every `base..tip` pair and fail if any ref needs checks.

### 4. Medium — The 30-day rule stops "a new plan is a new class" for plans that pay early (CONFIRMED)

- **Where:** `TimeDepositCalculator.kt:14,28,38-39`; `CLAUDE.md:53-55`; `docs/ASSIGNMENT.md` ("for any existing plans", "extensible")
- **Problem:** The assignment limits the 30-day rule to *existing* plans; the calculator applies it to every plan, including injected ones. A probe policy paying 10.0 on a 10-day deposit left the balance at 1000.0, with no error. `CLAUDE.md:54-55` states this choice, but it is a wider reading of the assignment and is not recorded as an assumption.
- **Why it matters:** This is the extension the design advertises, and such a plan means editing the calculator, not adding a class. Its author also gets silent zero interest rather than a failure.
- **Action:** Move the rule to a wrapper or base policy applied to the three existing plans, which leaves behaviour unchanged. Or keep it and record the assumption, and qualify the KDoc at line 14.

### 5. Medium — `CLAUDE.md` describes most of the architecture in the present tense, but it does not exist (CONFIRMED)

- **Where:** `CLAUDE.md:8-9,17,28,36-42,50,90`; `pom.xml:36,85,91`; `Makefile:22`
- **Problem:** The docs say Kotlin 2.3, Ktor, Exposed, PostgreSQL, Flyway and Testcontainers; the POM has Kotlin 1.7.20 and none of the others. Missing: `application/`, `adapter/`, `config/`, `Money`, `Withdrawal`, `toMoney()`, `TransactionRunner`, the OpenAPI contract, `docs/TRADEOFFS.md` and any `*IT`. `make run` targets `LocalApplicationKt`, which does not exist.
- **Why it matters:** Those layering rules hold only because the code is absent, so they are untested. AI agents treat this file as fact, and `CLAUDE.md:90` sends assumptions to a file that does not exist, which is how finding 4 went unrecorded.
- **Action:** Mark the target state as planned, or add a "built so far" line, until the slices land. Create `docs/TRADEOFFS.md` now.

### 6. Medium — The policy contract is held by a comment: public policies give wrong answers and receive a mutable deposit (CONFIRMED)

- **Where:** `InterestPolicy.kt:8-10,15`; `BasicInterestPolicy.kt:8-9`; `StudentInterestPolicy.kt:8-9`; `TimeDepositCalculator.kt:23,28`
- **Problem:** "Not meant to be called directly" is unenforced on public classes: `BasicInterestPolicy` returns 0.83 and `StudentInterestPolicy` 2.5 for a 10-day deposit of 1000.0. Reading one plan takes two files, since Student's 31-365 window is split between policy and calculator. Policies also get the mutable `TimeDeposit`; a policy that set the balance to 5.0 and returned 1.0 ended at 1001.0, because line 23 reads the balance first.
- **Why it matters:** The first caller wanting a preview or a report will use a policy directly and get interest the business rule forbids. The ordering assumption appears in no signature.
- **Action:** Make the three policies `internal`, or fix via finding 4. Pass `balance` and `days` rather than the deposit, since `TimeDeposit` itself is pinned.

### 7. Medium — The hooks test the working tree, not the commits being pushed (CONFIRMED)

- **Where:** `.githooks/pre-push:28`; `.githooks/pre-commit:6`
- **Problem:** Nothing checks out `$tip` before `make verify`. Pushing branch `code` with `main` checked out, the stub logged `HEAD` at the base commit while the range under review ended at the pushed tip. `pre-commit` likewise tests the working tree, not the index.
- **Why it matters:** A new class left unstaged passes both hooks while the pushed commit fails to compile, and pushing a non-current branch verifies the wrong code entirely.
- **Action:** Run `make verify` in a temporary worktree at `$tip`, or at minimum abort when the tree is dirty or `HEAD` differs from `$tip`.

### 8. Medium — The gate cannot see changes to itself (CONFIRMED)

- **Where:** `.githooks/pre-push:9`; `.githooks/pre-commit:4`; `.claude/commands/quality-gate.md:31-39`
- **Problem:** `code_paths` omits `.githooks`, `.claude`, `mvnw` and `mvnw.cmd`, and is duplicated across two hooks. A push replacing `pre-push` with `exit 0`, and a push rewriting `mvnw`, each exited 0 with no check. Because `CLAUDE.md`, `docs/` and `.claude/` are also excluded, a docs-only push never reaches the `spec` and `docs` classes from the hook.
- **Why it matters:** The gate and the build wrapper can change unreviewed, though `mvnw` is plainly a build file under the `CLAUDE.md:80-81` skip rule. The hook-driven range gets `spec` and `docs` review only when code changes too.
- **Action:** Add `.githooks`, `.claude`, `mvnw` and `mvnw.cmd`, and keep the list in one place both hooks read. State in `CLAUDE.md` that docs-only pushes need a manual `/quality-gate`.

### 9. Low — Constructor injection replaces plans, and a wiring mistake looks like an unknown plan (CONFIRMED)

- **Where:** `TimeDepositCalculator.kt:17-19,30,41-42`; `CLAUDE.md:49,53`
- **Problem:** `TimeDepositCalculator(listOf(vip))` drops the defaults, and a `basic` deposit stayed at 1000.0 with no signal, because `?: 0.0` cannot tell a wiring gap from an unknown plan. `associateBy` also lets a duplicate plan type win silently (1099.0 in the probe). `defaultPolicies()` in the domain conflicts in letter with "wiring in `config/` only", though hard constraint 2 forces it.
- **Why it matters:** Once `config/` exists, one wiring error stops interest for a whole plan while every test of the new plan passes. Hard constraint 2 requires `?: 0.0`, but not the silent duplicate.
- **Action:** Reject duplicate plan types at construction; this is wiring, not the invalid input hard constraint 5 excludes. Say in `CLAUDE.md:53` that injection must include `defaultPolicies()`, and note the `defaultPolicies()` exception at line 49.

### 10. Low — Nothing enforces the layering, and packages form a cycle that a generic rule would flag (CONFIRMED)

- **Where:** `CLAUDE.md:45`; `pom.xml:81-104`; `TimeDepositCalculator.kt:3-6`; `BasicInterestPolicy.kt:3`, `InterestPolicy.kt:3`, `PremiumInterestPolicy.kt:3`, `StudentInterestPolicy.kt:3`
- **Problem:** A single Maven module means the compiler cannot stop `domain` importing Ktor or Exposed. `org.ikigaidigital` and `org.ikigaidigital.domain.interest` import each other. There is no class-level cycle, and `CLAUDE.md:51-52` explains the cause.
- **Why it matters:** Today's clean graph is true only by discipline, and that is about to be tested as adapters land. `INTEREST_FREE_DAYS` is public with no outside user, which invites a policy to import the calculator and create a real cycle.
- **Action:** Add a small layering test when the first adapter lands, stating the reason as `CLAUDE.md` requires, with the root package treated as domain. Make `INTEREST_FREE_DAYS` private.

## Architectural claims tested

| Claim                                                                  | Result             | Evidence                                                                              |
| ---------------------------------------------------------------------- | ------------------ | ------------------------------------------------------------------------------------- |
| Domain is plain Kotlin, no Ktor, Exposed or serialization              | Honoured           | Only `java.math` and `org.ikigaidigital` imports in `src/main`                        |
| Dependencies point inwards                                             | Honoured           | No violations; only domain code exists                                                |
| `TimeDeposit` unchanged (constraint 1)                                 | Honoured           | Not in the range diff                                                                 |
| `updateBalance` signature and no-arg constructor (constraint 2)        | Honoured           | `javap` shows both public                                                             |
| `updateBalance` exact arithmetic and rounding (constraint 2)           | Honoured (read)    | `balance * rate / 12` order, guards and `BigDecimal(Double)` traced against original  |
| Policies hold plan-specific rules only                                 | Honoured           | Each holds rate and threshold; shared arithmetic is in `monthlyShare`                 |
| 30-day period enforced by the calculator                               | Honoured           | `TimeDepositCalculator.kt:28`; scope questioned in finding 4                          |
| All policies have the same shape                                       | Honoured           | One property, one method, no extra parameters or side effects                         |
| New plan by `defaultPolicies()` or constructor                         | Partly             | Works from day 31 only (finding 4); injection replaces (finding 9)                    |
| No hidden state, no direct environment reads                           | Honoured           | No globals, `getenv` or properties; `TimeDeposit.balance` is the only mutation        |
| No god objects, no cycle-breaking imports                              | Honoured           | Largest file is 44 lines; no function-level imports; no class-level cycle             |
| `SKIP_AI_GATE=1` and `--no-verify` behave as stated                    | Honoured (read)    | `.githooks/pre-push:30-33`; `--no-verify` is native git                               |
| No Claude or connection: hook says so and asks                         | Honoured (read)    | `.githooks/pre-push:50-67`; aborts without a TTY                                      |
| Only changes not yet reviewed are reviewed again                       | Honoured           | Ancestor checks fall back to the full range after a rebase                            |
| Push stops on reports; skipped only without code or build files        | **Not honoured**   | Findings 2, 3 and 8                                                                   |
| `make verify` runs `*IT` under Failsafe                                | **Not honoured**   | Finding 1                                                                             |
| Adapter, `config/`, Money, transaction and contract rules              | Untestable         | Code does not exist (finding 5)                                                       |
| `.gitignore` entry for `.claude/worktrees/`                            | Fine               | Matches a live worktree; no tracked file affected                                     |

## Limits of this review

- Differential old-versus-new testing of `updateBalance` was not run here; that claim rests on reading the code and on the characterization tests, which passed in the `make verify` run.
- Hook results come from stubs, so they prove the hook's control flow, not the behaviour of the real `claude` or `make`.
