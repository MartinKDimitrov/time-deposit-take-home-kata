# Verify findings closed — full mode, range `c4ea358..HEAD`

Source of the finding list: every version of `docs/ai-reviews/*.md` committed in the range, recovered with
`git show <commit>:docs/ai-reviews/<file>.md` for `a2f1fda`, `05191df`, `33f165a`, `64d71b2`, `58e448e`, `33eb49c`
(later pushes overwrite the files, so the earlier rounds exist only in history). Acceptance was checked against
`docs/TRADEOFFS.md` and `docs/PRODUCT.md` at HEAD: a finding counts as accepted only where the document names it
and gives a reason.

Docker was available (server 29.9.2). `make verify` was run once in a private worktree at HEAD: **green**
(unit + `TimeDepositApiIT` against real PostgreSQL 17.11) — the clean end-to-end happy path still succeeds.
All mutation work ran in private worktrees, since removed. Verdicts marked **(run)** were established by executing
code; **(read)** verdicts were established by reading only.

## Not closed

### 1. High — The invariant the lock fix exists for is pinned by no test (test-quality 2, ranges 33f165a..64d71b2 and 64d71b2..58e448e)

- **Where:** `src/main/kotlin/org/ikigaidigital/application/service/UpdateAllBalancesService.kt:13-17`
- **Input (run):** split the read and the write into two transactions in a worktree —

  ```kotlin
  override fun updateAllBalances() {
      val timeDeposits = transactionRunner.inTransaction { timeDepositRepository.findAllForUpdate() }
      calculator.updateBalance(timeDeposits)
      transactionRunner.inTransaction { timeDepositRepository.updateBalances(timeDeposits) }
  }
  ```

- **Observed:** `a balance update waits for a concurrent change and builds on it` → **PASSED**. Confirmed
  independently by a sibling agent: the whole suite stays green with this mutation.
- **Why it matters:** this mutant releases the row lock before the write, which is exactly the lost update
  `9df7b3f` was written to prevent. The test only proves "read after the other session commits"; it cannot see a
  change that lands between the read and the write. `docs/TRADEOFFS.md` records the *lock order* gap and the
  *timeout path* gap, but **not** this one, so it is open rather than accepted.
- **Action:** add a test that commits a change after the POST has read but before it writes, or record the gap in
  `docs/TRADEOFFS.md` with the same explicitness as the neighbouring entries.

### 2. Medium — No empty-table case anywhere (test-quality 3, range 664cee8..0bce5c7)

- **Where:** `src/test/kotlin/org/ikigaidigital/adapter/web/TimeDepositApiIT.kt` — four tests, all on the same
  3-row seed.
- **Observed (read):** the only test files in the repository are `TimeDepositCalculatorTest.kt` and
  `TimeDepositApiIT.kt`; no test does `GET /time-deposits` or `POST /time-deposits/balance-updates` on an empty
  table. `docs/TRADEOFFS.md` does not name this gap.
- **Why it matters:** `[]` versus `null` serialization and the "nothing to lock" path are untested; the previous
  `verify-findings-closed.md` already listed this as "STILL OPEN (untouched)" and it is still untouched.
- **Action:** two short tests, or one line in `docs/TRADEOFFS.md`.

### 3. Low — `DatabaseConfig` is still a `data class`; the password half of the credentials finding is neither fixed nor recorded (adversarial-code 3, range 664cee8..0bce5c7)

- **Where:** `src/main/kotlin/org/ikigaidigital/adapter/persistence/DatabaseSetup.kt:8-14`
- **Observed (read):** `data class DatabaseConfig(val url, val user, val password, …)`; `toString()` therefore
  prints the password. `docs/TRADEOFFS.md` ("Default database credentials in `Application.kt`") records the
  fallback credentials with a reason, but says nothing about the `toString` leak.
- **Why it matters:** latent only — nothing logs the config today. It bites the first time someone adds
  `log.info("$databaseConfig")`.
- **Action:** override `toString`, or add half a line to the existing TRADEOFFS entry.

### 4. Low — The Exposed `Database` is never unregistered; only the Flyway half of the leak finding is recorded (adversarial-code 5, range 664cee8..0bce5c7)

- **Where:** `src/main/kotlin/org/ikigaidigital/adapter/persistence/ExposedTransactionRunner.kt:10`
  (`Database.connect(dataSource)` with no `TransactionManager.closeAndUnregister`);
  `src/main/kotlin/org/ikigaidigital/config/TimeDepositModule.kt:24-26` (`migrate` still outside a try/close).
- **Observed (read):** `docs/TRADEOFFS.md` records "No cleanup if the migration fails at start-up" with a reason
  (the JVM exits), which closes the Flyway half. The global Exposed registration — one more entry per
  `testApplication`, four per IT run today — is recorded nowhere.
- **Action:** unregister in the same `ApplicationStopped` hook, or record it.

### 5. Low — The documented "last policy wins" rule is enforced and pinned by nothing (adversarial-architecture 9, adversarial-code 3, test-quality 1)

- **Where:** `src/main/kotlin/org/ikigaidigital/TimeDepositCalculator.kt:19` (`policies.associateBy { it.planType }`)
- **Input (run):** mutated to `policies.reversed().associateBy { it.planType }` (first-wins).
- **Observed:** `make test T=TimeDepositCalculatorTest` → **SURVIVED**, suite green.
- **Why it matters:** this is *partly* accepted. `docs/TRADEOFFS.md` ("Duplicate plan types: the last policy in the
  list wins, silently … No guard was added, because rejecting invalid input is out of scope. Sources:
  adversarial-architecture finding 9, verify-findings-closed, test-quality (push review)") names the finding and
  gives a reason for not adding a *guard*. It does not say the documented rule is also untested, and the KDoc at
  line 15 states it as fact. One three-line test would pin it.

## New defects found while checking

None. Every mutation that should have been caught was caught, and the one survivor (lock ordering) is already
recorded in `docs/TRADEOFFS.md`.

## Closed

### Proven by running code

| Item | Description | Verdict |
| --- | --- | --- |
| adversarial-code 1 (`a2f1fda`), test-quality 1-3 (`a2f1fda`) | Rounding mode, `BigDecimal(Double)` and `balance * rate / 12` order unpinned → `7178178` | **CLOSED (run)** |
| test-quality 4 (`a2f1fda`) | "Round the interest, not the balance" hung on one row | **CLOSED (run)** |
| test-quality 5 (`a2f1fda`) | Plan-type matching pinned by one literal | **CLOSED (run)** |
| adversarial-code 1 (`33f165a`) | Concurrent POST loses a month of interest → `9df7b3f` | **CLOSED (run)** |
| test-quality 1 (`64d71b2`), test-quality 1 (`58e448e`) | Concurrency test could pass without the lock (`delay(500)`) → `bc47765` | **CLOSED (run)** |
| adversarial-code 2 (`a2f1fda`) | A balance written by a policy was silently lost | **CLOSED (run)** |
| adversarial-architecture 1 (`a2f1fda`) | `make verify` never ran `*IT`, yet reported success | **CLOSED (run)** |
| test-quality 3 Info (`33eb49c`) | Lock-poll timeout path untested / might hang | **CLOSED (run)** |

Evidence, all in private worktrees:

- **Arithmetic pins.** Each mutation applied singly, `make test T=TimeDepositCalculatorTest`:
  `BigDecimal.valueOf` → killed (`expected: 18.01`); `HALF_EVEN` → killed (`expected: 150.13`); `HALF_DOWN` →
  killed (`150.13`); `balance * (rate / 12)` → killed (`18.01`, `10.02`); `balance / 12 * rate` → killed (`10.02`,
  `3.62`); `planType.lowercase()` lookup → killed (`1000.0`, the `Basic` row); round-the-sum instead of the addend
  → killed (`6.01`, `18.01`); `days < 30` → killed; student `365→364` and `365→366` → killed; premium `>`→`>=` and
  `45→46` → killed; `MONTHS_IN_YEAR 12→11` → killed; basic rate `0.01→0.02` → killed. **13 of 14 mutants killed**;
  the only survivor is item 5 above. The five survivors that `adversarial-code` reported in `a2f1fda` are all dead.
- **The lock.** `make verify` restricted to the lock IT. Dropping `.forUpdate()` from `findAllForUpdate`:
  **failed 3/3** with `expected: 2001.67 but was: 1000.83` — the exact symptom the fix was written for; a sibling
  agent reports 5/5 green restored. Pointing the service at `findAll()` instead: **failed 2/2**, same message. The
  fix is reachable through the real entry point (HTTP POST → route → service → Exposed → PostgreSQL), not only
  through a unit harness. Making the poll condition unsatisfiable (`> 100`): the test **fails fast** with
  `IllegalStateException: The balance update never waited for the row lock`; it does not hang.
- **Policy write ordering.** A policy doing `deposit.balance -= 5.0; return 10.0` on `1000.0, 31 days` now yields
  **1005.0** (was 1010.0 before `6951c57`), so the policy's write survives, as the comment at
  `TimeDepositCalculator.kt:23` claims.
- **Failsafe.** `maven-failsafe-plugin` 3.5.2 is bound to `integration-test` and `verify`; `target/failsafe-reports`
  shows `Tests run: 1` for a single selected IT and the run goes red on a mutation, so `*IT` really executes.

### Knowingly accepted, named with a reason in `docs/TRADEOFFS.md` (read)

| Item | Where it is recorded |
| --- | --- |
| adversarial-architecture 2 — review stop is not a gate | "The stop after a review is a reminder, not a block" (also `CLAUDE.md:87`) |
| adversarial-architecture 3 — multi-ref push checks the last ref only | "Known limits of the hooks → Single branch" (code at `.githooks/pre-push:12-21` still overwrites `base`/`tip`) |
| adversarial-architecture 7 — hooks test the working tree | "Known limits of the hooks → Working tree, not the pushed commit" |
| adversarial-architecture 4 / adversarial-code 4 — 30-day rule widened to every plan | `PRODUCT.md` assumption A1, cross-referenced from TRADEOFFS |
| adversarial-architecture 6 — policies callable directly | TRADEOFFS "The 30-day rule lives in the calculator"; policies are now `internal` (`6951c57`), `INTEREST_FREE_DAYS` is `private` |
| adversarial-architecture 10 — nothing enforces the layering | TRADEOFFS "No layering test". Checked by grep at HEAD: no `io.ktor`, `org.jetbrains.exposed`, `kotlinx.serialization`, `com.zaxxer` or `org.flywaydb` import under `domain/` or `application/` — the rule holds today |
| adversarial-code 2 (`33f165a`) — `Double` loses cents above ~9e13 | TRADEOFFS Money, "A `Double` holds whole cents exactly up to about 9 × 10¹³" |
| adversarial-code 4 (`33f165a`) — `Dispatchers.IO` wider than the Hikari pool | TRADEOFFS "Blocking JDBC on `Dispatchers.IO`" |
| adversarial-code 2 (`64d71b2`) — one `UPDATE` per row, unchanged rows included | TRADEOFFS "Balance update: all deposits in memory, locked, one `UPDATE` per row" |
| adversarial-code 1 (`64d71b2`) — `GET` does not wait for the lock | TRADEOFFS "Reads do not wait for the lock" |
| test-quality 3 (`58e448e`) / 2 (`64d71b2`) — lock order (`orderBy(id)`) untested | TRADEOFFS "Lock order is not tested". Confirmed **(run)**: removing `.orderBy(TimeDepositsTable.id)` leaves the lock test green — accepted, as documented |
| test-quality 1 (`33eb49c`) — lock poll not scoped to the balance update | TRADEOFFS "The lock test waits for any blocked session" |
| test-quality 2 (`33f165a`) — IT boundaries exercised on one side only | TRADEOFFS "Integration data sits on the paying side of each boundary"; the boundaries themselves are pinned in the unit tests, proven by the mutations above |
| adversarial-architecture 5 — `CLAUDE.md` described an architecture that did not exist | Now it does: Ktor/Exposed/Flyway/Testcontainers in `pom.xml`, `application/`, `adapter/`, `config/`, `Money`, `Withdrawal`, `toMoney()`, `TransactionRunner`, the OpenAPI contract, `docs/TRADEOFFS.md` and one `*IT` all present; `make run` targets `LocalApplicationKt`, which exists at `src/test/kotlin/org/ikigaidigital/LocalApplication.kt` |
| adversarial-architecture 8 — the gate could not see changes to itself | **Fixed**, not accepted: `.githooks/code-paths` now lists `src pom.xml .mvn mvnw mvnw.cmd Makefile .githooks .claude` and is read by both hooks. The docs-only half is recorded in TRADEOFFS "Docs-only pushes skip the gate" |

### Closed by reading (no mutation run)

| Item | Description |
| --- | --- |
| test-quality 1 (`33f165a`) | POST never checked that withdrawals and other columns survive → `77db64b`: the POST test now re-reads the full state through `GET` and compares the whole JSON body, including both withdrawal rows and every `days`/`planType`. The assertion is real (it is the same comparison the passing GET test makes) but the "adapter replaces the aggregate" mutation was not run |
| test-quality 4 (`64d71b2`) / 4 (`58e448e`) | POST status not asserted in the lock test → now `assertThat(update.await().status).isEqualTo(HttpStatusCode.NoContent)` at `TimeDepositApiIT.kt:101` |
| test-quality 5 (`58e448e`) | `delay(500)` cost and import order → no fixed sleep remains; imports alphabetical |
| test-quality 4 (`33f165a`) | Idempotency test asserts neither status nor contract → still true (`TimeDepositApiIT.kt:85-89`), but the first POST test covers both; reported Low and left |
| adversarial-code 6, 7 (`33f165a`) | Discarded `update` row count; schema/DTO contracts held only by tests — latent, unchanged, unreachable today |

## Over-correction and happy path

- No guard was added anywhere in this range, so there is no rejection path to over-correct. The only tightening is
  the row lock, and `GET` deliberately does not take it.
- Happy path **(run)**: `make verify` at HEAD is green end to end — `GET` returns all three deposits with their
  withdrawals and validates against the contract, `POST` returns `204` and leaves the full state as hand-computed
  (1000.83 / 1004.17 / 1000.00), and the lock test passes. The success path is reachable.
- Valid case in the tightened area **(run)**: with `.forUpdate()` in place a normal single POST still succeeds and
  the repeated-POST test still accumulates to 1001.66 — the lock does not block the ordinary path.

## Not audited — ran out of budget

- `adversarial-architecture` 2, 3 and 7 were verified only as *recorded in TRADEOFFS*; the hook behaviour itself
  was not re-run (it mutates refs, and the earlier report already proved it with stubs).
- `test-quality 1 (33f165a)`: the "adapter cascades and loses withdrawals" mutation was not applied; the verdict
  rests on reading the assertion.
- `adversarial-code 2 (33f165a)`: the `1e15 + 0.01` precision probe was not run; the limit is accepted in TRADEOFFS
  on arithmetic grounds.
