# test-quality (push mode, read only)

Range: 0bce5c7..33f165a. File: src/test/kotlin/org/ikigaidigital/adapter/web/TimeDepositApiIT.kt.
No mutation runs in push mode. Mutation table: not run. Every finding is SUSPECTED.

## Mutation results
| Function | Mutation | Caught |
| --- | --- | --- |
| ExposedTimeDepositRepository.findAllForUpdate | drop `.forUpdate()` | SUSPECTED no (flaky at best), see 1 |
| UpdateAllBalancesService.updateAllBalances | call `findAll()` instead of `findAllForUpdate()` | SUSPECTED no (flaky at best), see 1 |
| ExposedTimeDepositRepository.findAllForUpdate | drop `.orderBy(id)` | SUSPECTED no, see 2 |

Check for `full`: apply each mutation in a worktree and run `make test T=` / `make verify` about 20 times for the concurrency IT.

### 1. High - SUSPECTED: concurrency test may pass without the lock
- Where: TimeDepositApiIT.kt, `concurrent POSTs each add a month - no update is lost`
- Problem: two `launch`ed POSTs through the in-memory testApplication client on a tiny 3-row table. The read-modify-write window is a few milliseconds and the launches may be serialised by the test dispatcher. Nothing forces the two transactions to overlap, so a missing `FOR UPDATE` probably passes most runs.
- Why it matters: commit 9df7b3f exists only to fix this lost update, and the test is its sole regression guard (no unit test checks the service uses `findAllForUpdate`).
- Action: in `full`, mutate away the lock and loop the test; if it passes, widen the window (more concurrent calls, e.g. 10-20, run on `Dispatchers.Default`) or hold the first transaction open with a JDBC `SELECT ... FOR UPDATE` plus a latch and assert the POST blocks until commit.

### 2. Medium - SUSPECTED: lock-ordering (deadlock-avoidance) claim is untested
- Where: ExposedTimeDepositRepository.kt `findAllForUpdate` comment; no test.
- Problem: removing `orderBy(id)` leaves every test green, because concurrency is never reliably produced and rows are inserted in id order anyway.
- Action: accept as documented-only, or cover it in the strengthened test from finding 1.

### 3. Low - POST responses are not asserted in the repeated and concurrent tests
- Where: `POST is not idempotent` and `concurrent POSTs` ignore the response status. A 500 on the first call and success on the second would give 1000 + 0.83*1, not 1001.66, so the balance check does catch it indirectly; the status is still not checked.
- Action: assert 204 on every call.

### 4. Low - the balance check covers one row
- Where: `balances()[1]` (premium) only. Rows 0 and 2 are not checked under concurrency (a lost update on the basic row would go unnoticed). Compare the whole map, e.g. basic 1001.66, premium 1008.34 (check the expected numbers), student 1000.00.

### 5. Low - fixture notes
- `apiTest` TRUNCATEs and re-seeds each test (good isolation). The container is shared via a companion `start()` and never stopped; Testcontainers' Ryuk handles cleanup.
- Seed data is hand-built and mirrors the code's assumptions (31, 46 and 366 days). That is intentional boundary coverage but not the real demo data.
- The expected values in the single-POST test (1000.83, 1004.17) are hardcoded, not derived from code. Good.

## Genuinely strong
- GET and single-POST tests compare the full JSON body to literal expected values, and every response is validated against the OpenAPI contract. Both can fail.
- The post-update state check (commit 77db64b) covers all three plans, the withdrawals and the unchanged student row.
- Tests run against real PostgreSQL, with no mocks.

## Not checked (push mode)
Coverage numbers, whether `TimeDepositCalculatorTest` characterises the boundaries (30/31, 45/46, 365/366), and the mutation score of the domain code.
