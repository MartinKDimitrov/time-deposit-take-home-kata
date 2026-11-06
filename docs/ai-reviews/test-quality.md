# Test quality review (push mode, read only)

Range: 664cee8..0bce5c7. Files: `TimeDepositApiIT.kt`, `LocalApplication.kt`, `R__demo_data.sql`.
Nothing was run or mutated. Every finding is SUSPECTED; the proving check for `/quality-gate full` is named in each.

Verdict: sound. The tests are not tautological and the expected values are hand-derived. The gaps are about what POST and GET do not check, not about tests that cannot fail.

## Mutation results

Not measured in push mode. Mutations that `full` mode should run (all in a scratch copy):

| Function                      | Mutation                                              | Expected to be caught by                  |
| ----------------------------- | ----------------------------------------------------- | ----------------------------------------- |
| balance-update repository     | write back only the first deposit                     | POST test (yes, balances 2 and 3 differ)  |
| balance-update repository     | delete or recreate withdrawals while saving           | NOT caught (see finding 1)                |
| GET mapper                    | order withdrawals by id descending                    | GET test (yes, 2 withdrawals)             |
| GET mapper                    | serialize balance without scale 2                     | GET test (yes, JSON literal compare)      |
| route POST                    | return 200 and a body instead of 204                  | POST test (yes, status and contract)      |
| route POST                    | run the update twice per call                         | idempotency test (yes, 1001.66 vs 1002.5) |
| `timeDepositModule`           | skip the transaction (partial update on failure)      | NOT caught (no failure test)              |

## Findings

### 1. Medium — POST never checks that withdrawals and other columns survive
- **Where**: `TimeDepositApiIT.kt:56-68`; `balances()` reads only `id, balance`.
- **Problem**: after a balance update, withdrawals, `days` and `plan_type` are not re-read. The seeded deposit 1 has two withdrawals, but only the balance is asserted.
- **Why it matters**: a persistence adapter that replaces or cascades the aggregate would lose withdrawals, and the suite would stay green.
- **Action**: after the POST, assert the `withdrawals` row count and the deposits' `days` and `plan_type`, or call GET and compare to the original except for balances. In `full` mode, prove it by mutating the adapter as in the table.

### 2. Medium — The 30/31, 45/46 and 365/366 boundaries are exercised on one side only, and only through the API
- **Where**: `TimeDepositApiIT.kt:84`.
- **Problem**: the IT seeds 31, 46 and 366 days. At 366 the student pays nothing, but a mutation of the student threshold to `> 366` or `>= 365` is not distinguished here.
- **Why it matters**: if the unit tests do not cover the other side of every boundary, a threshold shifted by one survives. This file does not check it.
- **Action**: in `full` mode, mutate each threshold by one in a scratch copy and run `make test`. CLAUDE.md requires 30/31, 45/46 and 365/366 in unit tests, so confirm they exist.

### 3. Low — No empty or negative cases at the API level
- **Where**: `TimeDepositApiIT.kt`.
- **Problem**: the IT has no GET on an empty table (`[]` against the contract), no POST on an empty table (still 204), and no deposit without withdrawals checked after POST. Deposits 2 and 3 have none, and GET covers only the serialization of `[]`.
- **Why it matters**: the empty-list path is the most likely place for a null-versus-empty serialization bug.
- **Action**: add one test, `GET on empty database returns []`, and one for POST on an empty table.

### 4. Low — The idempotency test does not assert the status or the contract
- **Where**: `TimeDepositApiIT.kt:71-75`.
- **Problem**: the `repeat(2)` responses are discarded, and only deposit 1 is asserted (1001.66, hand-derived and correct).
- **Why it matters**: low. The first POST test already covers status and contract.
- **Action**: optional. Assert 204 on both calls.

### 5. Low — Resources are not cleaned up, and the container is shared across tests with TRUNCATE
- **Where**: `TimeDepositApiIT.kt:26-31, 77-94`.
- **Problem**: `timeDepositModule` creates a Hikari pool per test, and no test closes it. The static container is not stopped (Ryuk cleans it up).
- **Why it matters**: it works today. It becomes a connection-leak risk as ITs are added.
- **Action**: close the data source on `ApplicationStopped`, if the module exposes it.

### 6. Info — Seed and `LocalApplication` are untested by design
- **Where**: `LocalApplication.kt`, `R__demo_data.sql`.
- **Problem**: the seed is only used by `make run`. The ITs do not load `db/seed` and also TRUNCATE before inserting. I checked the comment arithmetic: 10000 x 1%/12 = 8.33, 20000 x 3%/12 = 50, 100000 x 5%/12 = 416.67.
- **Why it matters**: a seed drift would not fail any test. It is demo data, so the risk is small.
- **Action**: none required. A smoke check that the seed applies against the real migrations would catch drift; add one only if the seed grows.

## Genuinely strong

- The GET test compares the full body as JSON, including the scale-2 literals (`1000.00`, `50.50`), and also validates against the OpenAPI contract. A wrong field, order or scale fails it.
- The POST test's expected balances are hand-computed (1000.83, 1004.17, 1000.00), not derived from the code under test.
- `containsExactlyEntriesOf` on `BigDecimal` is scale-sensitive and strict.
- It runs against real PostgreSQL through Testcontainers, as CLAUDE.md requires, and the Flyway migrations actually run (`startApplication()`).
- The contract validator reports an error for an undeclared path or status, so extra or missing endpoints are caught.
