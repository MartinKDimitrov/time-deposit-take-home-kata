# test-quality (push mode, read only)

Range: 33f165a..64d71b2. File: src/test/kotlin/org/ikigaidigital/adapter/web/TimeDepositApiIT.kt.
No mutation runs in push mode. Mutation table: not run. Every finding is SUSPECTED.

Verdict: the new lock test is a real test. Reasoning by hand: without `forUpdate()` the POST reads 1000.00 under MVCC,
its UPDATE then blocks on the other connection's row lock and overwrites 2000.00 with 1000.83, so the assertion
(2001.67) fails. The full-state check on POST is also strong (all three plans, withdrawals untouched, 1004.17 and
1000.83 are correct for Double + HALF_UP).

### 1. Medium — the lock test can pass on broken code when the POST is slow
- **Where**: TimeDepositApiIT.kt, `a balance update waits for a concurrent change and builds on it`, `delay(500)`.
- **Problem**: the test assumes the POST has reached the lock within 500 ms. On a cold or loaded machine (first request,
  Hikari warm-up, CI) the other connection commits 2000.00 first. The POST then reads 2000.00 with or without
  `forUpdate()`, so the test goes green without the lock. The failure mode is a silent pass, not a flaky failure.
- **Why it matters**: the lock is what stops lost balance updates; a test that only proves it on a fast machine
  will not catch its removal in CI.
- **Action**: instead of a fixed sleep, poll `pg_stat_activity` (`wait_event_type = 'Lock'`) until the POST is waiting,
  with a timeout, then run the UPDATE and commit. In `full` mode: remove `.forUpdate()` and run the test
  repeatedly under CPU load to see whether it ever stays green.

### 2. Low — the lock is not shown to be held until the write
- **Where**: UpdateAllBalancesService.kt (`findAllForUpdate` and `updateBalances` in one transaction), test above.
- **Problem**: the test blocks the read and releases before the POST reads, so it cannot see a change that lands between
  read and write. Splitting the use case into two transactions, or weakening the lock, still gives 2001.67
  (SUSPECTED).
- **Why it matters**: the atomic read-modify-write is the guarantee; only "read after the other commit" is proven.
- **Action**: full mode: mutate to run the read and the write in separate `inTransaction` calls and see whether the
  suite still passes. Add a test that commits a change after the POST has read but before it writes, or accept it as
  covered by the transaction-runner design.

### 3. Low — the concurrent-POST test was deleted, nothing covers two updates racing
- **Where**: the removed `concurrent POSTs each add a month - no update is lost`.
- **Problem**: the code comment claims locking in id order prevents deadlock between two updates. No test runs two
  updates at once now. The old test was weak (the sequential non-idempotent test already asserts 1001.66), so the
  loss is small.
- **Why it matters**: removing `orderBy(id)` from `findAllForUpdate` would go unnoticed.
- **Action**: full mode: drop `orderBy` in `findAllForUpdate` and run the suite; likely uncaught and acceptable
  (a deadlock needs two rows locked in opposite order, hard to provoke deterministically).

### 4. Low — POST status is not checked in the lock test
- **Where**: `launch { client.post(...) }`, response discarded.
- **Problem**: a 500 response is only caught indirectly, because the balance would be 2000.00. Fine today, but an
  assertion on 204 would make the intent explicit.
- **Action**: capture the response and assert `NoContent`.

### 5. Info — cost and style
- `delay(500)` adds half a second to every run (resolved by finding 1). The `kotlinx.coroutines.delay` import is out of
  alphabetical order, a style nit.

## Genuinely strong
- POST and GET compare the full JSON state of all three deposits and their withdrawals, with response validation
  against the OpenAPI contract, on real PostgreSQL.
- The non-idempotency test pins accumulation across two calls.
