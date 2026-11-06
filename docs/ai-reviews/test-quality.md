# test-quality (push mode, read only)

Range: 64d71b2..58e448e. File: src/test/kotlin/org/ikigaidigital/adapter/web/TimeDepositApiIT.kt (commit bc47765).
No mutation runs in push mode. Mutation table: not run. Every finding is SUSPECTED.

Verdict: the change closes finding 1 of the previous report. The `delay(500)` race, which could pass silently without the
lock, is replaced by a poll on `pg_stat_activity` (`wait_event_type = 'Lock'`). The test now also asserts the POST
returned 204 (before it only joined). Reasoning by hand, the test still fails without `.forUpdate()`: the POST reads
1000.00, its UPDATE blocks on the other session's row lock (so the poll also sees a Lock wait on the broken code),
the other session commits 2000.00, and the POST overwrites it with 1000.83, so `2001.67` is not reached.

### 1. Low — the poll is not scoped to the balance update
- **Where**: TimeDepositApiIT.kt, `sessionsWaitingForLock()`.
- **Problem**: `count(*) ... WHERE wait_event_type = 'Lock'` counts any session in the database. It does not filter by
  database or by query, and it cannot tell which statement waits. It is correct today because the tests run one after the
  other and only the POST can wait for a lock (SUSPECTED).
- **Why it matters**: a second blocked session (parallel test class sharing the container, a future second lock test)
  would release the test too early. The failure mode would then be a silent pass again.
- **Action**: filter on `datname = current_database()` and `query LIKE '%time_deposits%'` (or the POST's
  `application_name`); full mode: run the test once with `.forUpdate()` removed and once with it, to confirm red/green.

### 2. Low — the lock is still proven only for "read after the other commit"
- **Where**: UpdateAllBalancesService.kt (`findAllForUpdate` and `updateBalances` in one transaction), the lock test.
- **Problem**: unchanged from the previous report. Running the read and the write in two transactions, or weakening the
  lock after the read, still ends at 2001.67 (SUSPECTED).
- **Why it matters**: the atomic read-modify-write is the guarantee, and a change landing between read and write is untested.
- **Action**: full mode: mutate to two `inTransaction` calls and run the suite. If it stays green, add a test that commits
  a change after the POST has read, or record the gap in `docs/TRADEOFFS.md`.

### 3. Info — timeout and failure paths of the new helper are untested and unproven
- **Where**: `awaitUpdateWaitingForLock()`, 100 attempts of 50 ms (5 s).
- **Problem**: on timeout it throws inside `coroutineScope` while the POST coroutine is still blocked in JDBC on
  `Dispatchers.IO`. Whether the scope then ends promptly or hangs until the connection closes is not shown (SUSPECTED).
  A cold CI run slower than 5 s fails loudly, not silently, so this is a flake risk and not a correctness risk.
- **Why it matters**: a hang would burn CI time instead of reporting the error.
- **Action**: full mode: make the helper's condition never true (for example `> 100`) and check that the test fails
  with the message within a few seconds.

## Strong parts
- The POST test checks the whole state (all three plans, withdrawals untouched) against hand-computed values (1000.83,
  1004.17) and the contract; it is not derived from the implementation.
- The GET test compares the full JSON body and validates the contract.
- The lock test now waits on an observed database state instead of a guessed time, and checks the status code.
