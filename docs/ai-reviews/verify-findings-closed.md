# Verify findings closed - fix commits 9df7b3f and 77db64b

Range `0bce5c7..33f165a`. Mode: push (reading only, nothing built or run, no Docker). Every verdict is SUSPECTED until
`/quality-gate full` runs the checks named below. Findings source: `docs/ai-reviews/adversarial-code.md` (item 1) and
`docs/ai-reviews/test-quality.md` (item 1). This file replaces the report of the previous round (see git history).

## Not closed

| Item | Description | Status |
| ---- | ----------- | ------ |
| adversarial-code 1 (test side) | The new concurrency IT may pass without the lock | PARTIAL (SUSPECTED) |

### adversarial-code 1 - production fix CLOSED, proof PARTIAL
- **Fix read**: `UpdateAllBalancesService.kt:15` now calls `findAllForUpdate()`; the adapter does `selectAll().orderBy(id).forUpdate()`
  inside the same transaction. In PostgreSQL READ COMMITTED the second transaction waits on the row lock and then re-reads the
  committed row, so it sees the first update's balance. Ordering by id gives one lock order. Reachable through the real entry
  point (the route calls the service). GET still uses the unlocked `findAll()`, correct.
- **Weak proof**: `TimeDepositApiIT.kt:88-95` fires two POSTs with `launch` and asserts 1001.66. The race window without the
  lock is a few milliseconds and the test does not force the overlap, so it may be green on the broken code (the test is
  not shown to fail first).
- **Full-mode check**: in a scratch copy, switch the service back to `findAll()` and run the concurrent test about 20 times
  (`make verify`); if it passes even once, make the overlap deterministic (for example a 3-5 request fan-out, or hold the
  first transaction open with `pg_sleep` in a test-only hook).

## Closed

| Item | Description | Status |
| ---- | ----------- | ------ |
| adversarial-code 1 | Lost update on concurrent POST: `FOR UPDATE` in id order | CLOSED (code), see above for the test |
| test-quality 1 | POST did not check withdrawals, `days`, `plan_type` | CLOSED |

- **test-quality 1**: the POST test now reads back through `GET /time-deposits` and compares the full JSON (ids, plan types,
  days, withdrawals, scale-2 balances). Expected values are hand-derived (1000.83, 1004.17, 1000.00; days 31/46/366 unchanged;
  both withdrawals present), so the assertion would catch a repository that drops withdrawals or alters `days`. The
  direct-SQL `balances()` helper is still used by the idempotency test. Valid-path check: the clean seed -> POST -> GET path is
  the same one the test exercises, so the happy path is still reachable.
- **Over-correction**: `forUpdate()` on a plain `SELECT ... ORDER BY` has no join or aggregate, so PostgreSQL accepts it. An
  empty table returns an empty list and the update is a no-op. Not exercised (no empty-table IT, test-quality 3 stays open
  from the previous round).

## Other items seen while checking (not part of the list)
- Compilation of `.forUpdate()` has no import in `ExposedTimeDepositRepository.kt`; it must be a `Query` member in Exposed 1.x.
  The pre-push `make verify` is said to be green, so I did not rebuild; confirm that `make verify` passed on 9df7b3f.
- `TimeDepositApiIT.kt:22-23` the `kotlinx.coroutines` imports sit after `java.*`; harmless, no formatter in `check` complains today.
- Open from the review, unchanged: adversarial-code 2-7 (Double precision cap, default credentials, pool size vs `Dispatchers.IO`,
  pool leak on failed start, ignored update count); test-quality 2-6.
