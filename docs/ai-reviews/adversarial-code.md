# adversarial-code (push mode, read only)

Range: 664cee8..0bce5c7. Production files only. Nothing was built or run, so every finding is SUSPECTED.
Verdict: no blocking defect. One medium finding (lost update on concurrent balance updates), the rest are low or latent.

### 1. Medium — Concurrent POST /balance-updates can lose a month of interest (SUSPECTED)
- **Where**: application/service/UpdateAllBalancesService.kt:15-19; adapter/persistence/ExposedTimeDepositRepository.kt:13-23
- **Problem**: the transaction reads all rows, computes in memory and writes absolute balances. It takes no row lock. PostgreSQL defaults to READ COMMITTED, so two overlapping calls both read balance B and both write B+interest. Two calls then add one month of interest, not two. The use case is documented "Not idempotent", so a double click, a retry or two instances makes the result depend on timing.
- **Why it matters**: silent wrong balances with no error. Not reachable in a single sequential client.
- **Action**: SELECT ... FOR UPDATE in the update path (Exposed `forUpdate()` on a dedicated `findAllForUpdate`), or run the transaction at SERIALIZABLE with a retry. Full mode: IT firing two parallel POSTs and asserting the balance equals two months of interest.

### 2. Low — Balance round trip through Double loses cents above about 9e13 (SUSPECTED)
- **Where**: ExposedTimeDepositRepository.kt:29-33 (`toDouble()`), Tables.kt (`decimal(.., 19, 2)`), Money.kt:10
- **Problem**: the schema allows 17 integer digits, Double holds about 15-17 significant digits. A balance such as 123456789012345.67 is read as Double, written back and changes in the last cent, even for a deposit that earns no interest. This follows from hard constraint 1/2 (Double), but nothing caps the column to the precision Double can hold.
- **Why it matters**: only reachable with very large balances; GET and the update path then disagree with the stored value.
- **Action**: record the limit in docs/TRADEOFFS.md or narrow the column to NUMERIC(15,2). Full mode: probe with 1e15 + 0.01.

### 3. Low — Hard-coded default DB credentials and port (SUSPECTED)
- **Where**: Application.kt:11-15, 17
- **Problem**: if DB_PASSWORD is not set, the app silently connects as timedeposits/timedeposits. The port 8080 is fixed. `DatabaseConfig` is a data class, so a later `log(config)` prints the password in `toString()`.
- **Why it matters**: a production misconfiguration is masked instead of failing fast. The password leak is latent.
- **Action**: keep the defaults for `make run` only, or fail when DB_* is missing outside local runs. Override `toString` or use a non-data class if the config is ever logged.

### 4. Low — Dispatchers.IO is wider than the Hikari pool (SUSPECTED)
- **Where**: adapter/web/TimeDepositRoutes.kt:21,25; adapter/persistence/DatabaseSetup.kt:17-23
- **Problem**: IO allows 64 blocking threads, HikariCP defaults to 10 connections. Under more than 10 concurrent requests the extra threads wait up to 30 s for a connection, then fail with an unhandled exception (HTTP 500). Cancelling the request does not interrupt the blocked JDBC call.
- **Why it matters**: load only; invisible in tests.
- **Action**: set `maximumPoolSize` explicitly, or limit with `Dispatchers.IO.limitedParallelism(poolSize)`.

### 5. Low — Setup failure leaks the pool; Exposed Database is never disconnected (SUSPECTED)
- **Where**: config/TimeDepositModule.kt:24-27; ExposedTransactionRunner.kt:10
- **Problem**: `createDataSource` runs before `migrate`. If Flyway throws (bad migration, DB down), `ApplicationStopped` may never fire, so the pool stays open. `Database.connect` registers a global Exposed entry that nothing removes (`TransactionManager.closeAndUnregister`). Every `testApplication` in the IT suite adds one.
- **Why it matters**: leaked connections and threads in test and failed-start scenarios; harmless in normal production.
- **Action**: wrap migrate in try/close; unregister the database in the same stop hook.

### 6. Latent — updateBalances silently ignores ids that no longer exist
- **Where**: ExposedTimeDepositRepository.kt:15-21
- **Problem**: `update` returns the affected row count and it is discarded. Today the transaction makes this unreachable; with a delete endpoint or a non-transactional caller a balance update would vanish without a trace.
- **Action**: if rows are ever deletable, check the count. Otherwise leave as is.

### 7. Latent — Two silent contracts without a compile-time link
- **Where**: Tables.kt vs V1__create_time_deposit_tables.sql; TimeDepositResponses.kt vs openapi yaml
- **Problem**: column names and scales (`decimal(19,2)`, `varchar(50)`) are spelled twice, in SQL and in Exposed, with no schema check. The DTO shape and the contract are held together only by the IT response validation. A rename in one place fails at runtime only.
- **Action**: none now. If the schema grows, add an IT that reads every mapped column.

## Looked at and found sound
- BigDecimalAsNumberSerializer: `toPlainString()` in an unquoted literal gives valid JSON numbers (no exponent, negatives are fine). The unused `deserialize` is harmless.
- `Double.toMoney()` uses `BigDecimal.valueOf` (shortest decimal string), so there is no binary-expansion artefact. HALF_UP matches the calculator. NaN/Infinity cannot come from the database.
- GET path: two queries in one transaction, `groupBy` plus `orEmpty()` gives deposits without withdrawals an empty list. Withdrawals are immutable, so READ COMMITTED skew between the two selects is harmless. Ordering by id is explicit.
- Update path is atomic: one failing row rolls back all rows. The calculator mutates the list in place on purpose, and the repository persists those same objects.
- SQL: FK and index present; `date` as a column name is legal in PostgreSQL and Exposed quotes it; `plan_type` varchar(50) matches the mapping.
- Dependency direction: application has no adapter imports; the transaction boundary sits in the services, not the adapters.
