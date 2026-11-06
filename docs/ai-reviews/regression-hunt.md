# Regression hunt: 7d6fc90..8404993 (push) - InterestPolicy strategy extraction

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

---

# Regression hunt: c4ea358..HEAD (full)

Range `c4ea358..HEAD` (`4ca5339` language-copy removal, `8404993` InterestPolicy extraction, `6951c57` internal
policies, `9df7b3f` FOR UPDATE locking fix, `bc47765` lock test). Mode: full. Worktree detached at HEAD, removed
again; `MAVEN_ARGS=-o make verify` run **once** at HEAD: green (24 tests). Main checkout otherwise untouched.

## Verdict

**No regression in the change set itself.** Hard constraints 1 and 2 hold, proven by execution. The locking fix is
effective and its test is not vacuous. Three defects found, all in the persistence/web slice that the refactor and
the lock fix sit on; all are rare-input or load-tier.

## Proofs that came back clean (CONFIRMED)

| Claim | Evidence |
| --- | --- |
| Refactored `updateBalance` is bit-for-bit identical to the c4ea358 original | Differential test vs a verbatim copy of `kotlin/src/main/kotlin/org/ikigaidigital/TimeDepositCalculator.kt@c4ea358`. **71,104 grid cases + 300,000 random fuzz cases, 0 differences**, compared on `Double.toRawBits()` (so `-0.0`/`+0.0` and NaN payloads count as differences) and on exception class. Grid: 16 plan types (incl. `unknown`, `Basic`, `BASIC`, `""`, trailing space, Turkish dotless `ı`, NUL-suffixed), 101 day values (-1000..-995, 0..70, 360..370, 44/45/46/47, 364/365/366/367, 730/731, 10000, `Int.MAX_VALUE`, `Int.MIN_VALUE`), 44 balances (0.0, -0.0, negatives, `Double.MIN_VALUE`, 1e15, 1e16, 1.7e18, HALF_UP tie values 150.0/18.0/10.0/3.6). |
| Non-finite and extreme values behave identically | `NaN`, `±Infinity`, `Double.MAX_VALUE` × 4 plans × 4 day values: same `NumberFormatException` from `BigDecimal(double)`, same bits otherwise. |
| Empty list, duplicate and **aliased** list elements identical | Same object three times in one list compounds identically in both implementations. |
| Hard constraint 2 - no-arg constructor | `javap` shows `public org.ikigaidigital.TimeDepositCalculator();` is still emitted (all-default primary constructor). A real Java consumer compiled and run against `target/classes` printed `1 1000.83 / 2 1004.17 / 3 1000.0`. |
| The locking fix works through the real HTTP route | 20 concurrent `POST /time-deposits/balance-updates` -> all 204, final balances `{1=1016.80, 2=1086.71, 3=1000.00}`, exactly equal to 20 sequential months computed independently. **No lost update.** |
| The lock fix's own test is an effective proof | Mutant: `.forUpdate()` deleted from `ExposedTimeDepositRepository.findAllForUpdate`. `TimeDepositApiIT.a balance update waits for a concurrent change and builds on it` fails with `expected: 2001.67 but was: 1000.83` - the exact lost-update symptom. Restored: **5/5 consecutive runs green**. This closes the "check for full mode" left open in `verify-findings-closed.md`. |
| Happy path on empty input | Empty table -> `POST` 204, `GET` 200 `[]`. |
| Money round trip is exact for real balances | All 200,001 balances from `0.00` to `2000000.00` survive DB `NUMERIC(19,2)` -> `Double` -> calculator -> `toMoney()` -> `NUMERIC(19,2)` with **0 cent mismatches**. |
| Atomicity | A mid-batch DB failure rolls the whole run back (see finding 2); the two tightened guards (`FOR UPDATE` read + `toMoney` write) do not conflict - the fully clean case still succeeds. |

## Findings

### 1. Medium - `GET /time-deposits` reports a balance that was never stored

- **Where** `src/main/kotlin/org/ikigaidigital/adapter/web/TimeDepositResponses.kt:30` (`timeDeposit.balance.toMoney()`), `src/main/kotlin/org/ikigaidigital/adapter/persistence/ExposedTimeDepositRepository.kt:38` (`.toDouble()`), `src/main/resources/db/migration/V1__create_time_deposit_tables.sql:9` (`NUMERIC(19,2)`).
- **Problem** CONFIRMED through the real Ktor route. Stored `999999999999999.99` -> `GET` body `{"balance":1000000000000000.00}`, DB row untouched. Stored `99999999999999999.99` -> `GET` body `100000000000000000.00` - 18 integer digits, a value `NUMERIC(19,2)` can never hold. A read-only endpoint silently misreports above ~9e13 because the balance must pass through the frozen `Double`.
- **Why it matters** `docs/TRADEOFFS.md` documents the *write-side* cent loss above 9 × 10¹³ as "far beyond any real deposit", but the column itself accepts 17 integer digits, so the schema permits rows the API cannot report faithfully. Silent, no error.
- **Action** Cap the column in a new migration at `NUMERIC(15,2)` (max 9.99e12, safely below the 9e13 `Double` cent limit) so the schema cannot hold an unreportable value; or add the read-side misreport to `docs/TRADEOFFS.md`.

### 2. Medium - one row at the column maximum makes `POST /time-deposits/balance-updates` 500 and rolls back the monthly run for every deposit

- **Where** `src/main/kotlin/org/ikigaidigital/application/service/UpdateAllBalancesService.kt:14-18`, `src/main/kotlin/org/ikigaidigital/adapter/persistence/ExposedTimeDepositRepository.kt:26-32`.
- **Problem** CONFIRMED. Table seeded with `(1,'basic',31,1000.00), (2,'basic',31,99999999999999999.99), (3,'basic',31,2000.00)`; `POST` -> **500 Internal Server Error**, `ExposedSQLException: PSQLException: ERROR: numeric field overflow`. Balances after: `{1=1000.00, 2=99999999999999999.99, 3=2000.00}` - atomic rollback works, but ids 1 and 3 never get their interest and never will. Cause: `toMoney()` turns `1.0E17` into `100000000000000000.00`, precision 20 > the column's 19.
- **Why it matters** A single row the schema legitimately accepts permanently blocks the only write endpoint for the whole portfolio, with no partial progress and no operator-visible cause. Interaction with the lock fix: the failing attempt now takes a table-wide `FOR UPDATE` lock *before* it fails, so every retry re-locks all rows.
- **Action** The same column cap as finding 1 makes this unreachable. Otherwise record it in `docs/TRADEOFFS.md` as a known 500.

### 3. Medium - the locking fix lets one stuck lock make `GET /time-deposits` unavailable

- **Where** `src/main/kotlin/org/ikigaidigital/adapter/persistence/ExposedTimeDepositRepository.kt:20-24` (`.forUpdate()`, no lock timeout), `src/main/kotlin/org/ikigaidigital/adapter/persistence/DatabaseSetup.kt:16-23` (Hikari defaults: `maximumPoolSize=10`, no `lock_timeout`/`statement_timeout`).
- **Problem** CONFIRMED. One foreign session holds `SELECT * FROM time_deposits FOR UPDATE`; 10 concurrent `POST`s then block inside `findAllForUpdate()` (`pg_stat_activity` shows exactly **10 sessions with `wait_event_type='Lock'`** = the whole pool). `GET /time-deposits`, which takes no locks at all, returned **nothing in 10 s** (`TimeoutCancellationException`) against an **11 ms** baseline measured in the same test. After the foreign lock is released all 10 `POST`s return 204 with correct balances (`{1=1008.37, 2=1042.46}` = 10 exact months), so the fix is right - the read endpoint is collateral damage.
- **Why it matters** `FOR UPDATE` waits indefinitely and now blocks at the *read*, so a POST holds a pool connection for an unbounded time. 10 concurrent POSTs (well under `Dispatchers.IO`'s 64 threads) are enough to make the service, including the read endpoint, unavailable. `docs/TRADEOFFS.md` ("extra requests wait for a connection, which is acceptable at this scale") assumes bounded waits, which `FOR UPDATE` removed.
- **Action** Bound the wait: `connectionInitSql = "SET lock_timeout = '3s'"` (or `dataSourceProperties["options"] = "-c lock_timeout=3000"`) in `createDataSource`, and set `maximumPoolSize` explicitly rather than relying on the default. Cheapest alternative: record the unbounded wait in `docs/TRADEOFFS.md`, which it currently is not.

### 4. Low - `sessionsWaitingForLock()` counts lock waiters cluster-wide

- **Where** `src/test/kotlin/org/ikigaidigital/adapter/web/TimeDepositApiIT.kt:158-165`.
- **Problem** PLAUSIBLE (not reproducible today, and already noted in `verify-findings-closed.md`). `SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'` has no `datname`/`pid`/query filter. Harmless while the container is dedicated and methods run sequentially; a false positive would only make the test wait less, and the `2001.67` assertion still fails on broken code (verified by the mutant run).
- **Why it matters** Becomes a real false pass if failsafe ever forks in parallel or a second IT class shares the container.
- **Action** Add `AND datname = current_database() AND pid <> pg_backend_pid()`, or match the query text.

## Checked, not a problem

- `TimeDepositCalculator` is a shared singleton in `TimeDepositModule`; `policiesByPlanType` is an immutable map of
  stateless policies and `updateBalance` keeps no state - safe for concurrent requests.
- `6951c57` (internal policies): `InterestPolicy` and `defaultPolicies()` stay public, so
  `TimeDepositCalculator(defaultPolicies() + MyPolicy())` still works from outside the module; only `monthlyShare`
  is module-private (an external policy author re-implements `balance * rate / 12`).
- `orderBy(id)` + `forUpdate()`: both app transactions lock in the same order, so app-vs-app deadlock is impossible;
  valid SQL confirmed by the passing IT.
- `4ca5339`: no stale references to `kotlin/`, `java/`, `python/`, `typescript/`, `c#/` in `Makefile`, `pom.xml`,
  `README.md`, `.githooks/code-paths` or `docs/`.
- `/swagger` vs hard constraint 3 - already recorded as `docs-claims-audit` finding 2; not repeated.
