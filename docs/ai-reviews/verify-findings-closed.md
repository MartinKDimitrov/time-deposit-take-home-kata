# Verify findings closed - fix bc47765 (wait for the row lock instead of sleeping)

Range `64d71b2..58e448e`. Mode: push (reading only, nothing built or run). Every verdict is SUSPECTED until
`/quality-gate full` runs `make verify`. Sources: `test-quality.md` (items 1-5, range 33f165a..64d71b2) and the open
items of the previous `verify-findings-closed.md`. The range changes only `TimeDepositApiIT.kt` and the review reports;
no production code changed, so the open production items are unchanged.

## Not closed

| Item                  | Description                                                       | Status                         |
| --------------------- | ----------------------------------------------------------------- | ------------------------------ |
| test-quality 2        | Lock not shown to be held from read to write (one transaction)    | STILL OPEN (accepted, Low)     |
| test-quality 3        | No test of two updates racing; `orderBy(id)` removal unnoticed    | STILL OPEN (accepted, Low)     |
| adversarial-code 2    | Double round trip loses cents above ~9e13; column not capped      | STILL OPEN (not in TRADEOFFS)  |
| adversarial-code 3    | Default DB credentials, data-class `toString` would print password | STILL OPEN                   |
| adversarial-code 4    | `Dispatchers.IO` wider than the Hikari pool                       | STILL OPEN                     |
| adversarial-code 5    | Pool leaks if `migrate` throws                                    | STILL OPEN                     |
| older API-slice items | no empty-table case, one-sided boundaries in the IT               | STILL OPEN (untouched)         |

Items 2 and 3 were marked "accept or cover in full mode"; the fix did not touch them. Not a regression.

## Closed (by reading)

| Item           | Description                                             | Status |
| -------------- | ------------------------------------------------------- | ------ |
| test-quality 1 | `delay(500)` lets the lock test pass on broken code     | CLOSED |
| test-quality 4 | POST status not checked in the lock test                | CLOSED |
| test-quality 5 | `delay` import out of order; 500 ms added to every run  | CLOSED |

- test-quality 1: `awaitUpdateWaitingForLock()` polls `pg_stat_activity WHERE wait_event_type = 'Lock'` every 50 ms, up
  to 100 times, and fails with an error if no session ever waits. The UPDATE and commit on the second connection now
  happen only after the POST is blocked. Cause fixed, not the symptom.
- The poll discriminates in both directions. With `forUpdate()` the POST blocks in its SELECT, so it has read nothing,
  and it then reads 2000.00 (+1.67 = 2001.67). Without `forUpdate()` the POST's SELECT does not block, it reads
  1000.00, and its UPDATE then blocks on the row lock held by the other connection. The poll still sees a waiter, the
  other side commits 2000.00, and the POST overwrites it with 1000.83, so the 2001.67 assertion fails. A slow POST
  cannot cause a false pass any more: the test just waits longer (up to 5 s).
- test-quality 4: `async` + `assertThat(update.await().status).isEqualTo(HttpStatusCode.NoContent)`.
- test-quality 5: imports are alphabetical (`async`, `coroutineScope`, `delay`); no fixed sleep remains.

## Over-correction and happy path (by reading)

- Only the lock test changed; GET and the other POST tests are untouched.
- `sessionsWaitingForLock()` opens a new connection per poll and closes it (`use`). The count has no filter on the
  database or the session: another waiting session would give a false "waiting". Not possible here: the container is
  dedicated to this class and tests run sequentially, and the only other connection (`other`) holds the lock but does
  not wait.
- Not run: the clean end-to-end flow (`make verify`). The pre-push hook runs it, so it is presumed green.

## New defects found while checking

- Low: `sessionsWaitingForLock` is a shared-state check (any session). Fine today; if the class gets parallel
  tests on the same container, filter by `datname` or by query text.
- Low: the file has no trailing newline (`\ No newline at end of file`), a style nit.
- Still missing from `docs/TRADEOFFS.md`: one line on `FOR UPDATE` locking every row on each POST.

## Check for full mode

Remove `.forUpdate()` in a scratch copy and run `make verify T=TimeDepositApiIT` a few times under CPU load: the test
must fail every time. Then restore it and run: it must pass every time.
