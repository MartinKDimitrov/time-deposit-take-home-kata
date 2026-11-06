# Verify findings closed - fixes 9df7b3f, 77db64b, 94e5f11

Range `33f165a..64d71b2`. Mode: push (reading only, nothing built or run). Every verdict is from reading the diff
and the findings, so each is SUSPECTED until `/quality-gate full` runs `make verify`.
Findings sources (as of 33f165a): `adversarial-code.md` (items 1-7) and `test-quality.md` (items 1-6) for the API
slice. The older reports (`adversarial-architecture`, `regression-hunt`) concern the domain slice and were not touched
by these fixes.

## Not closed

| Item                  | Description                                                         | Status                       |
| --------------------- | ------------------------------------------------------------------- | ---------------------------- |
| adversarial-code 2    | Double round trip loses cents above ~9e13; column not capped        | STILL OPEN (not in TRADEOFFS)|
| adversarial-code 3    | Default DB credentials, data-class `toString` would print password  | STILL OPEN                   |
| adversarial-code 4    | `Dispatchers.IO` wider than the Hikari pool (no `maximumPoolSize`)  | STILL OPEN                   |
| adversarial-code 5    | Pool leaks if `migrate` throws; Exposed `Database` never unregistered | STILL OPEN                 |
| test-quality 2        | Boundaries one-sided in the IT (unit-test side not re-checked)      | STILL OPEN / not addressed   |
| test-quality 3        | No empty-table GET / POST case at API level                         | STILL OPEN                   |
| test-quality 4        | Idempotency test discards the response status                       | STILL OPEN (optional)        |
| test-quality 5        | Hikari pool closed on stop now; static container left to Ryuk       | PARTIAL (module closes pool) |
| adversarial-code 6, 7 | Latent items, "none now"                                            | Not required, left as is     |

Evidence for the open ones: `grep` finds no `maximumPoolSize`, `limitedParallelism`, `closeAndUnregister` or
`toString` override in `src/main`; `TimeDepositModule.kt:24-26` still calls `createDataSource` before `migrate` with
no try/close; `docs/TRADEOFFS.md` has no entry on the Double precision limit; `TimeDepositApiIT.kt` has four tests
(GET, POST, idempotency, concurrency) and none on an empty table.

## Closed (by reading)

| Item                | Description                                         | Status                        |
| ------------------- | --------------------------------------------------- | ----------------------------- |
| adversarial-code 1  | Lost update on concurrent POST                      | CLOSED (test has a weakness)  |
| test-quality 1      | POST did not check withdrawals, days, plan type     | CLOSED                        |

- adversarial-code 1: `UpdateAllBalancesService` now calls `findAllForUpdate()`, implemented with
  `selectAll().orderBy(id).forUpdate()` inside the same transaction as `updateBalances`. Id order keeps the lock order
  stable. GET still uses `findAll()` (no lock), so reads are not blocked. This is the cause fixed, not a symptom.
- The first test (9df7b3f, two parallel POSTs expecting 1001.66) could not tell fixed from unfixed reliably; 94e5f11
  replaced it. The new test holds `FOR UPDATE` on a second connection, starts the POST, sets balance 2000.00 and
  commits, then expects 2001.67. Without the fix the POST reads 1000.00 and writes 1000.83, so the test discriminates.
  Hand check: 2000 x 0.01 / 12 = 1.6667 -> 1.67. Correct.
- Weakness of that test (SUSPECTED): `delay(500)` is a timing assumption. If the POST reaches the database later than
  500 ms (cold JVM, slow CI), the other connection commits first, the POST reads 2000.00 even without the fix, and
  the test passes on the broken code. It can only give a false pass, never a false fail. Full mode: revert the repository
  to `findAll()` in a scratch copy and run `make verify T=TimeDepositApiIT` several times; a robust version would poll
  `pg_locks` / `pg_stat_activity` until the POST is waiting instead of sleeping.
- test-quality 1: the POST test now compares the full `GET /time-deposits` body (ids, plan types, balances, days and
  both withdrawals of deposit 1) with a hand-written JSON literal. A persistence change to withdrawals, days or plan
  type would fail it. The expected values (1000.83, 1004.17, 1000.00) are unchanged and hand-derived.

## Happy path and over-correction (by reading)

- `findAllForUpdate` returns the same mapping as `findAll`; the service flow (lock, calculate, update) is in one
  `inTransaction`. A sequential POST is unaffected. `FOR UPDATE` with `ORDER BY` and no join or `DISTINCT` is valid in
  PostgreSQL. The deposits-without-withdrawals case is untouched (withdrawals are not locked or read here).
- Not run: whether the full flow still passes `make verify`. The pre-push hook runs it, so it is presumed green.

## New defects found while checking

- `TimeDepositApiIT.kt:24` imports `kotlinx.coroutines.delay` after `launch`, out of alphabetical order (style only).
- Lock scope: `FOR UPDATE` locks every row on each POST; fine for this kata, worth one line in `docs/TRADEOFFS.md`
  (not there today).
