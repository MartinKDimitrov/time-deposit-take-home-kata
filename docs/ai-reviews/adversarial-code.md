# adversarial-code (full mode)

Range `c4ea358..HEAD`. Production code of the slice: calculator, policies, services, persistence and web adapters,
`V1` migration, OpenAPI contract. Proven by running the project's own `TimeDeposit.balance -> Double.toMoney()` and
`TimeDepositCalculator` in a throwaway worktree (`mvnw -o test`, scratch test, not committed). Docker was not
available, so no integration test was run; every claim below that needs PostgreSQL is marked.

Hard constraints 1, 2 and 5 were treated as given: `Double` arithmetic, `BigDecimal(Double)` + `HALF_UP` and the
absence of input validation are not reported as defects.

### 1. Medium — `NUMERIC(19,2)` accepts balances the `Double` path cannot carry; above ~9.9e16 every balance update fails
- **Where**: `src/main/resources/db/migration/V1__create_time_deposit_tables.sql:8` (`balance NUMERIC(19,2)`),
  `src/main/kotlin/org/ikigaidigital/adapter/persistence/ExposedTimeDepositRepository.kt:38` (`.toDouble()`) and `:29`
  (`deposit.balance.toMoney()`), `src/main/kotlin/org/ikigaidigital/domain/Money.kt:9`.
- **Problem**: the column allows 17 integer digits (up to `99999999999999999.99`); the `BigDecimal -> Double -> BigDecimal`
  round trip is exact only to about 15. Proven with the project's own functions:
  - first whole-cent balance that changes value: `253891529579430.91` -> `253891529579430.90` (a cent vanishes on **GET**,
    with no balance update at all, and is then persisted by the next `POST`);
  - `12345678901234567.89` -> `12345678901234568.00`; `9999999999999999.99` -> written as `10008333333333334.00` after a
    basic update — the cents are gone entirely;
  - `99999999999999000.00` (well inside the column) + one month of basic interest -> `100083333333332336.00`, **precision 20**.
    `NUMERIC(19,2)` permits 19 digits, so PostgreSQL rejects the `UPDATE` with `22003 numeric_value_out_of_range`
    (certain from the column definition; not executed here — no Docker).
- **Why it matters**: `updateAllBalances` is one transaction (`UpdateAllBalancesService.kt:16-20`), so the rejected row
  rolls back the whole batch: `POST /time-deposits/balance-updates` returns 500 and **no** deposit earns interest,
  every time, until someone edits that row by hand in SQL. There is no API path to fix or delete it (two endpoints only).
  The silent cent loss needs no exception at all. `docs/TRADEOFFS.md:60` records the ~9e13 `Double` limit but not that the
  schema permits ten thousand times that, and not the batch-wide failure.
- **Action**: constrain the column to what the `Double` path can carry — `NUMERIC(15,2)`, or a
  `CHECK (balance BETWEEN -99999999999999 AND 99999999999999)` — in a new migration, with a test at the boundary. If the
  wide column must stay, say in `docs/TRADEOFFS.md` that a balance above ~2.5e14 loses cents and one above ~9.9e16
  disables the balance-update endpoint for all deposits.

### 2. Low — Latent: the Exposed `Database` is registered globally and never unregistered
- **Where**: `src/main/kotlin/org/ikigaidigital/adapter/persistence/ExposedTransactionRunner.kt:11`,
  `src/main/kotlin/org/ikigaidigital/config/TimeDepositModule.kt:24-26`.
- **Problem**: `Database.connect` adds the database to a static registry (confirmed by `javap`: `TransactionManager$Companion`
  has `databases`, `getPrimaryDatabase()`, `getDefaultDatabase()` and `closeAndUnregister(Database)`).
  `timeDepositModule` closes the Hikari pool on `ApplicationStopped` but never calls `closeAndUnregister`, and it can run
  more than once per JVM — `TimeDepositApiIT` installs it four times, each with its own pool, all four left registered.
- **Why it matters**: correct today only because `inTransaction` always names its database. The first `transaction { }`
  written without the database argument — a new repository helper, a second `TransactionRunner`, a migration script —
  resolves through the registry and can land on a database whose pool is already closed: `SQLException: pool has been
  closed`, in a code path that compiles and reads correctly. SUSPECTED for the resolution order (not executed).
- **Action**: `monitor.subscribe(ApplicationStopped) { TransactionManager.closeAndUnregister(database); dataSource.close() }`,
  exposing the `Database` from the runner, and keep the explicit-database rule in a comment.

### 3. Low — `BigDecimalAsNumberSerializer.deserialize` accepts what the contract forbids, and is unused
- **Where**: `src/main/kotlin/org/ikigaidigital/adapter/web/BigDecimalAsNumberSerializer.kt:26-27`.
- **Problem**: no request body carries money today, so `deserialize` is dead. It reads
  `decodeJsonElement().jsonPrimitive.content`, which does not distinguish a JSON number from a JSON string: `"1000.50"`
  (quoted) parses as `1000.50`, although `type: number` in the contract rejects it; `null` and a non-numeric string throw
  `IllegalArgumentException`/`NumberFormatException` out of the framing phase instead of a serialization error.
  `encoder as JsonEncoder` (line 23) also means any second `ContentNegotiation` format (CBOR, protobuf) turns a response
  into a `ClassCastException`.
- **Why it matters**: the first endpoint with a money field in its body would accept bodies the contract rejects — a
  producer/consumer split that only the contract validator on the *response* side would ever notice, i.e. not at all.
- **Action**: delete `deserialize` while nothing needs it (CLAUDE.md: no unused code), or check the primitive with
  `jsonPrimitive.isString` and fail; keep the `JsonEncoder` cast but assert the format in a comment.

### 4. Low — Latent: the repeatable demo seed carries hard-coded primary keys
- **Where**: `src/test/resources/db/seed/R__demo_data.sql:2-14`,
  `src/test/kotlin/org/ikigaidigital/LocalApplication.kt:14-16`.
- **Problem**: a Flyway `R__` migration re-runs whenever its checksum changes. The file inserts fixed ids 1-6 and 1-4 with
  no `ON CONFLICT`. Harmless today only because `LocalApplication` starts a throwaway container every time.
- **Why it matters**: the moment `make run` (or any environment) points `classpath:db/seed` at a database that survives a
  restart, editing a single demo amount makes the application fail to start with a duplicate-key violation, and the only
  cure is dropping the schema.
- **Action**: `INSERT ... ON CONFLICT (id) DO UPDATE`, or `TRUNCATE` at the top of the seed, or make it `V2__` and keep the
  seed out of persistent environments.

### 5. Info — The read model hands the web adapter a mutable domain object
- **Where**: `src/main/kotlin/org/ikigaidigital/application/port/inbound/GetAllTimeDepositsUseCase.kt:12-14`,
  `src/main/kotlin/org/ikigaidigital/adapter/web/TimeDepositResponses.kt:24-31`.
- **Problem**: `TimeDepositWithWithdrawals` carries the shared `TimeDeposit`, whose `balance` is a `var`. The mapper reads
  it once and immediately, so nothing is wrong; but the type lets an adapter mutate a value already read from the
  database, outside the transaction, with no effect on it.
- **Why it matters**: only if someone adds work between the use case and the mapper (filtering, a projection, caching the
  list). Hard constraint 1 forbids making the field immutable.
- **Action**: none now. If the GET path grows, map to `TimeDepositResponse` inside the transaction boundary.

## Looked at and found sound (proven or reasoned through; do not re-spend the pass here)

- **Day boundaries.** `days <= 30 -> 0` (`TimeDepositCalculator.kt:32`), premium `days > 45`, student `days <= 365`
  (`days < 366`) match `docs/PRODUCT.md:21-35` and the parameterised test at 30/31, 45/46 and 365/366 on both sides.
  A negative or zero `days` earns nothing, which is the same branch as day 0.
- **Rounding.** `roundToCents` (`BigDecimal(Double)`) and `toMoney` (`BigDecimal.valueOf`) really do disagree —
  2.675 -> 2.67 vs 2.68, and reachable interest amounts hit it (student balance 6.00 -> 0.01 vs 0.02) — but the calculator
  rounds the interest and `toMoney` then rounds a sum of two exact 2-decimal doubles. I searched every whole-cent balance
  from 0 to 2000.00 for a premium update: **0 mismatches** between the written value and the exact decimal sum. The
  divergence is pinned on purpose (`TimeDepositCalculatorTest` cases 18.00, 150.00, 10.00, 3.60) and recorded in
  `docs/TRADEOFFS.md:53-62`.
- **No reachable NaN/Infinity.** `BigDecimal(Double.NaN)` and `toMoney(Infinity)` throw `NumberFormatException` (verified),
  but `NUMERIC(19,2)` cannot produce a non-finite `Double`, and no arithmetic in range can overflow to one.
- **Locking.** `findAllForUpdate` orders by id *before* `forUpdate()`, so all callers take locks in one order and the two
  concurrent updates cannot deadlock; PostgreSQL's `LockRows` sits above the sort, and in any case both updaters run the
  same plan. The IT proves the second updater waits and builds on the committed value (2001.67), and `docs/TRADEOFFS.md:148`
  records that it was verified to fail with `forUpdate()` removed. Ids are immutable, so the EvalPlanQual re-read after the
  wait cannot reorder the locks.
- **Empty and single-row cases.** An empty table locks nothing, updates nothing and serialises as `[]`; one row takes the
  same path.
- **Two-decimal JSON.** `toPlainString()` can never emit exponent notation, and `toMoney` fixes scale 2, so `1000.50` stays
  `1000.50`. The contract does not constrain the scale (`type: number`, no `multipleOf`), but the IT's exact JSON comparison
  of `1000.00`/`1000.83`/`50.50` fails if the scale or the `toMoney` call is ever dropped — the guarantee is held by a test,
  not by the contract, and that test does hold it.
- **Contract/DTO drift.** `additionalProperties: false` plus all fields `required` means an added or renamed response field
  fails contract validation in `TimeDepositApiIT`. A *missing route* would not be a compile error (recorded in
  `docs/TRADEOFFS.md:40-43`).
- **Withdrawal mapping.** Grouped once by `timeDepositId` (no N+1), `orEmpty()` for a deposit with none, both queries
  ordered by id as the ports document; `LocalDate.toString()` is the ISO form `format: date` requires.
- **`updateBalances`.** One `UPDATE` per row by primary key, touching only `balance`; the rows are locked, so there is no
  lost update, and a duplicate id is impossible (PK). Rewriting unchanged rows costs a row version and nothing else.
- **GET during an update** reads the last committed balances under READ COMMITTED without waiting; withdrawals are never
  mutated, so the two statements of `GetAllTimeDepositsService` cannot return a mixed state today. Recorded in
  `docs/TRADEOFFS.md:124`.
