# Test quality review — `c4ea358..HEAD` (full mode)

Suite under review: `TimeDepositCalculatorTest` (20 unit tests) and `TimeDepositApiIT` (4 integration tests).
Baseline: `./mvnw -o verify` green in ~8 s in a detached worktree. Docker was available, so every integration
mutation below was really measured — nothing is unmeasured.

No mutation tool is configured (no `pitest`), and no coverage tool either (no `jacoco` in `pom.xml`), so all
45 mutations were made by hand in a scratch worktree. The main checkout was never modified.

## Mutation results

**40 of 45 killed (89%).** Excluding one unreachable-code mutant and one arguably equivalent mutant: 40/43 (93%).

### Interest rules — `TimeDepositCalculator`, `domain/interest/*` (23/23 killed)

| # | Function | Mutation | Caught |
| --- | --- | --- | --- |
| M01 | `TimeDepositCalculator.monthlyInterest` | `INTEREST_FREE_DAYS` 30 -> 29 | yes |
| M02 | `TimeDepositCalculator.monthlyInterest` | `INTEREST_FREE_DAYS` 30 -> 31 | yes |
| M03 | `TimeDepositCalculator.monthlyInterest` | `days <= 30` -> `days < 30` | yes |
| M04 | `TimeDepositCalculator.monthlyInterest` | interest-free guard removed | yes |
| M05 | `TimeDepositCalculator.monthlyInterest` | unknown plan falls back to basic instead of `0.0` | yes |
| M06 | `TimeDepositCalculator.defaultPolicies` | `PremiumInterestPolicy` dropped from the list | yes |
| M07 | `TimeDepositCalculator.monthlyInterest` | plan lookup made case-insensitive | yes |
| M08 | `TimeDepositCalculator.roundToCents` | `HALF_UP` -> `HALF_EVEN` | yes |
| M09 | `TimeDepositCalculator.roundToCents` | `HALF_UP` -> `HALF_DOWN` | yes |
| M10 | `TimeDepositCalculator.roundToCents` | `BigDecimal(d)` -> `BigDecimal.valueOf(d)` | yes |
| M11 | `TimeDepositCalculator.roundToCents` | `setScale(2)` -> `setScale(10)` (no cent rounding) | yes |
| M12 | `monthlyShare` | `balance * rate / 12` -> `balance * (rate / 12)` | yes |
| M13 | `monthlyShare` | `MONTHS_IN_YEAR` 12 -> 11 | yes |
| M14 | `BasicInterestPolicy` | rate 0.01 -> 0.02 | yes |
| M15 | `PremiumInterestPolicy.planType` | `"premium"` -> `"basic"` (plan fall-through) | yes |
| M16 | `StudentInterestPolicy` | `LAST_DAY_WITH_INTEREST` 365 -> 364 | yes |
| M17 | `StudentInterestPolicy` | `LAST_DAY_WITH_INTEREST` 365 -> 366 | yes |
| M18 | `StudentInterestPolicy` | `days <= 365` -> `days < 365` | yes |
| M19 | `StudentInterestPolicy` | rate 0.03 -> 0.05 | yes |
| M20 | `PremiumInterestPolicy` | `INTEREST_STARTS_AFTER_DAYS` 45 -> 44 | yes |
| M21 | `PremiumInterestPolicy` | `INTEREST_STARTS_AFTER_DAYS` 45 -> 46 | yes |
| M22 | `PremiumInterestPolicy` | `days > 45` -> `days >= 45` | yes |
| M23 | `PremiumInterestPolicy` | rate 0.05 -> 0.03 | yes |

### Persistence, application and web (17/22 killed)

| # | Function | Mutation | Caught |
| --- | --- | --- | --- |
| M24 | `ExposedTimeDepositRepository.findAllForUpdate` | `.forUpdate()` removed (row lock gone) | yes |
| M25 | `UpdateAllBalancesService.updateAllBalances` | `findAllForUpdate()` -> `findAll()` | yes |
| M26 | `ExposedTimeDepositRepository.updateBalances` | `where id eq deposit.id` -> all rows | yes |
| M27 | `UpdateAllBalancesService.updateAllBalances` | `updateBalances(...)` call removed (never persisted) | yes |
| M28 | `Double.toMoney` | `setScale(2)` -> `setScale(0)` | yes |
| M29 | `Double.toMoney` | `setScale(2)` -> `setScale(4)` | yes |
| **M30** | **`Double.toMoney`** | **`HALF_UP` -> `DOWN`** | **NO** |
| M31 | `GetAllTimeDepositsService` | `groupBy { it.timeDepositId }` -> `groupBy { it.id }` | yes |
| M32 | `TimeDepositWithWithdrawals.toResponse` | `withdrawals` -> `emptyList()` | yes |
| M33 | `ExposedTimeDepositRepository.findAll` | `orderBy(id)` -> `orderBy(id, DESC)` | yes |
| M34 | `Route.timeDepositRoutes` (POST) | `NoContent` -> `OK` | yes |
| M35 | `BigDecimalAsNumberSerializer.serialize` | `toPlainString()` -> `toDouble().toString()` | yes |
| **N01** | **`ExposedTimeDepositRepository.findAllForUpdate`** | **`orderBy(id)` removed, `.forUpdate()` kept** | **NO** |
| N02 | `ExposedWithdrawalRepository.findAll` | `orderBy(id)` -> `orderBy(id, DESC)` | yes |
| **N03** | **`UpdateAllBalancesService.updateAllBalances`** | **read and write split into two transactions** | **NO** |
| **N04** | **`GetAllTimeDepositsService.getAllTimeDeposits`** | **two reads split into two transactions** | **NO** |
| N05 | `TimeDepositResponse` | extra undeclared field added (`additionalProperties: false`) | yes |
| N06 | `Withdrawal.toResponse` | `date` shifted by one day | yes |
| N07 | `Withdrawal.toResponse` | `amount.stripTrailingZeros()` (scale lost) | yes |
| N08 | `DatabaseConfig.migrationLocations` | Flyway location pointed at nothing | yes |
| **P01** | **`BigDecimalAsNumberSerializer.deserialize`** | **body replaced with `throw`** | **NO** |
| P02 | `timeDepositModule` | calculator wired with `emptyList()` policies | yes |

Every mutation named in the brief was killed except the two transaction-boundary ones: the day boundaries
(30/31, 45/46, 365/366), every rate, the rounding mode, the arithmetic order, the plan-type match and
fall-through, the row lock itself, the scale-2 money mapping and the DTO/contract binding are all pinned.

## Findings

### 1. High — a lost update between read and write passes the concurrency test
- **Where** `src/test/kotlin/org/ikigaidigital/adapter/web/TimeDepositApiIT.kt:92-107` (test), `src/main/kotlin/org/ikigaidigital/application/service/UpdateAllBalancesService.kt:14-18` (code)
- **Problem** Mutant N03 moves the read and the write into separate transactions, so the `FOR UPDATE` lock is released before the new balance is written — the exact race the lock exists to prevent. The suite stayed **green**. The existing test only proves the update *waits* for a foreign lock, because the foreign session commits and then stops competing; nothing exercises two application writers overlapping.
- **Why it matters** `updateAllBalances` is the only write path in the service. Under N03 two concurrent `POST`s lose a month of interest: I added the missing test to the scratch copy and it fails with `expected: 1001.66 but was: 1000.83` against N03 while passing against unmutated `HEAD`. The lock fix of commit `9df7b3f` is therefore only half covered.
- **Action** Add the test, verified to kill N03:
  ```kotlin
  @Test
  fun `two concurrent balance updates both apply`() = apiTest {
      coroutineScope {
          val first = async { client.post("/time-deposits/balance-updates") }
          val second = async { client.post("/time-deposits/balance-updates") }
          assertThat(first.await().status).isEqualTo(HttpStatusCode.NoContent)
          assertThat(second.await().status).isEqualTo(HttpStatusCode.NoContent)
      }

      assertThat(balances()[1]).isEqualByComparingTo("1001.66")
  }
  ```

### 2. Medium — the documented deadlock guard is asserted by nothing
- **Where** `src/main/kotlin/org/ikigaidigital/adapter/persistence/ExposedTimeDepositRepository.kt:19-24`
- **Problem** The comment claims "Locking in id order keeps the lock order the same for every caller, so two updates cannot deadlock." Mutant N01 removes `orderBy(TimeDepositsTable.id)` while keeping `.forUpdate()` and the suite stayed **green**: the only ordering assertion in the suite goes through `findAll`, not `findAllForUpdate`.
- **Why it matters** A claim in a comment that no test holds up is the kind that survives a refactor and reappears as an intermittent production deadlock, which is unreproducible by construction.
- **Action** Either assert the emitted SQL orders by id, or make the concurrent-update test of finding 1 the home of the claim and move the comment to point at it. A deterministic deadlock test is not worth the flakiness.

### 3. Medium — read consistency of `GET /time-deposits` is untested
- **Where** `src/main/kotlin/org/ikigaidigital/application/service/GetAllTimeDepositsService.kt:15-21`
- **Problem** Mutant N04 splits the deposits read and the withdrawals read into two transactions; the suite stayed **green**. The single-transaction guarantee — a response that cannot show a withdrawal against a stale balance — is load-bearing for the endpoint's correctness and has no test.
- **Why it matters** `ARCHITECTURE.md` and the code comment both present the single transaction as a design decision. Without a test, the next person who introduces a second `inTransaction` for an unrelated reason gets a green build.
- **Action** Add one IT: hold an open transaction that inserts a withdrawal and raises a balance, issue the `GET` between the two statements, and assert the response shows neither rather than one of the two.

### 4. Medium — no coverage tool and no coverage floor in the commit gate
- **Where** `pom.xml` (no `jacoco`/`pitest`), `Makefile:15-20`
- **Problem** I could not run coverage because none is configured; blind spots below were found by reading plus mutation. `make test` and `make verify` have no coverage floor, so a commit that deletes tests still passes the `pre-commit` and `pre-push` hooks.
- **Why it matters** The repository's own standing rule is that the suite runs "with a coverage floor that fails the build". The mutation score here is genuinely good, which is exactly the state worth locking in before it erodes.
- **Action** Add `jacoco-maven-plugin` with `check` bound to `verify` and a floor at the current level; optionally add `pitest` so the 89% above becomes a tracked number rather than a one-off review.

### 5. Low — `BigDecimalAsNumberSerializer.deserialize` is dead code no test reaches
- **Where** `src/main/kotlin/org/ikigaidigital/adapter/web/BigDecimalAsNumberSerializer.kt:25-26`
- **Problem** Mutant P01 replaces the whole body with `throw UnsupportedOperationException` and the suite stays **green**. Neither endpoint accepts a request body, so the method is unreachable.
- **Why it matters** Small, but `CLAUDE.md` forbids unused code, and an untested deserializer invites someone to trust it when the first request DTO arrives.
- **Action** Delete `deserialize` (throw from it explicitly if `KSerializer` requires the override) or cover it with a round-trip unit test.

### 6. Low — the rounding mode of the single money conversion point is unpinned
- **Where** `src/main/kotlin/org/ikigaidigital/domain/Money.kt:10`
- **Problem** Mutant M30 (`HALF_UP` -> `DOWN`) **survived**, while the scale mutants M28/M29 were both killed. Every value that currently reaches `toMoney` already has at most two decimals — from `roundToCents` on the write path and from `NUMERIC(19,2)` on the read path — so the rounding mode is unreachable today.
- **Why it matters** `CLAUDE.md` makes `Double.toMoney()` the one conversion rule for money outside the domain. The day a caller passes an unrounded `Double` (a new policy, a wider column), the behaviour is whatever the last edit left behind.
- **Action** One unit test on `toMoney`: `1000.125.toMoney()` is `1000.13` and `(-1000.125).toMoney()` is `-1000.13`. This also documents that the rule is HALF_UP away from zero.

### 7. Low — the lock-wait poll can observe an unrelated lock
- **Where** `src/test/kotlin/org/ikigaidigital/adapter/web/TimeDepositApiIT.kt:158-165`
- **Problem** `SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'` is cluster-wide and matches any lock type, not the row lock on `time_deposits` by this test's writer. Should it ever return early, the foreign `UPDATE` would commit before the application read, and the test would assert the right number for the wrong reason.
- **Why it matters** It is sound today because Failsafe runs sequentially and the container is private to the class, but the guard's correctness rests on facts outside the test. This is the mechanism that makes the suite's best test trustworthy.
- **Action** Narrow the predicate to the relation and the database, e.g. join `pg_locks` on `relation = 'time_deposits'::regclass` with `granted = false`.

### 8. Low — missing empty and boundary inputs
- **Where** `src/test/kotlin/org/ikigaidigital/TimeDepositCalculatorTest.kt`, `src/test/kotlin/org/ikigaidigital/adapter/web/TimeDepositApiIT.kt:109-126`
- **Problem** No case for: `updateBalance(emptyList())`; `GET /time-deposits` against an empty table (does it return `[]` and still satisfy the contract?); a zero or negative balance, which the withdrawal model makes reachable; and `TimeDepositCalculator(emptyList())`.
- **Why it matters** The empty-table `GET` is the one with real exposure — it is the first request a fresh deployment serves, and it is the only input class the suite never sends.
- **Action** Add the empty-table `GET` to the IT (assert `[]` plus `assertMatchesContract`) and a zero-balance row to the parametrised unit cases.

### 9. Low — student interest is never earned through the HTTP stack
- **Where** `src/test/kotlin/org/ikigaidigital/adapter/web/TimeDepositApiIT.kt:116`
- **Problem** The seed uses `(3, 'student', 366, 1000.00)`, so the only student row in the integration fixture earns nothing. End to end, only basic-earning, premium-earning and student-not-earning are exercised.
- **Why it matters** Low, because the unit suite kills every student mutant. It matters only for a defect that lives between the calculator and the database for the student path specifically — e.g. a plan-type column truncation.
- **Action** Change the fixture row to a paying student age (e.g. 200 days) and move the "no interest after one year" case to a fourth row.

### 10. Low — the demo seed's arithmetic comments are not verified by anything
- **Where** `src/test/resources/db/seed/R__demo_data.sql:3-8`
- **Problem** The comments assert concrete amounts (`+8.33`, `+50.00`, `+416.67`). I recomputed all three and they are correct today, but no test reads this file — it exists for `make run` only.
- **Why it matters** These numbers are what a reviewer sees first in a demo. A rate change leaves them silently wrong.
- **Action** Either drop the amounts from the comments or add one unit test that runs `updateBalance` over the six seed rows and asserts the six resulting balances.

## What is genuinely strong

- **The characterization tests are real characterization tests, independently verified.** I re-implemented the
  original pre-refactor algorithm from `c4ea358:kotlin/.../TimeDepositCalculator.kt` separately and replayed all
  17 parametrised cases through it: **all 17 expected values match the original exactly**, including the four
  delicate ones (`150.00/31 -> 150.13` HALF_UP tie, `18.00/31 -> 18.01` for `BigDecimal(Double)`,
  `10.00/31 -> 10.02` and `3.60/46 -> 3.62` for the `balance * rate / 12` order). These are not values copied
  out of the new implementation — they pin the legacy behaviour the hard constraints require, and the three
  mutants that attack exactly those subtleties (M08, M10, M12) all die.
- **Every day boundary and every rate is pinned.** 23 of 23 interest mutants killed. The suite tests 30/31,
  45/46 and 365/366 as `CLAUDE.md` asks, and the `<=` versus `<` mutants die too, which is the part that
  usually survives.
- **The row lock is proven, not assumed.** M24 (`.forUpdate()` removed) and M25 (`findAll()` instead of
  `findAllForUpdate()`) both fail the concurrency IT. The test waits on `pg_stat_activity` instead of sleeping,
  so it is deterministic rather than timing-dependent. Its gap is scope (finding 1), not soundness.
- **The contract check has teeth.** N05 adds one undeclared field to the response DTO and the
  `additionalProperties: false` validation fails. Hard constraint 4 — contract and code cannot drift — is
  actually enforced, not merely stated.
- **No mocks anywhere.** Real PostgreSQL via Testcontainers, real Flyway, real Ktor, real Exposed, real Hikari.
  Nothing in this suite asserts a stub's own behaviour. N08 shows even the migration wiring is load-bearing.
- **Fixtures are honest.** `apiTest` truncates and re-seeds per test and the container is private to the class,
  so no test passes because of another's state; I found no fixture that hides work.
