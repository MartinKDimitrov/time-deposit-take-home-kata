# adversarial-code (push mode, read only)

Range 0bce5c7..33f165a. Files: ExposedTimeDepositRepository, TimeDepositRepository, UpdateAllBalancesService.
All findings are SUSPECTED (nothing was run).

### 1. Low — Reads in GET are not locked or snapshot-isolated against a running balance update
- **Where**: ExposedTimeDepositRepository.kt:14 (findAll), GetAllTimeDepositsService.kt:17-18
- **Problem**: findAll is a plain SELECT under PostgreSQL READ COMMITTED. The service issues two statements (withdrawals, then deposits). If the update commits between them, the response mixes states. A plain SELECT does not wait on the FOR UPDATE lock, so the lock only serialises updates against each other.
- **Why it matters**: A listing can show a balance that does not match the withdrawals returned with it. This is a narrow race and the data stays valid.
- **Action**: In full mode, run a concurrent IT (list while update) to confirm. If it is real, use REPEATABLE READ for the read transaction, or accept it and record it in TRADEOFFS.md.

### 2. Low — updateBalances writes every row, one statement per row, even when the balance did not change
- **Where**: ExposedTimeDepositRepository.kt:26-32
- **Problem**: N round-trips per call, and a new row version for each deposit that sat within the 30-day free period (balance unchanged). The rows are locked, so there is no lost update.
- **Why it matters**: Cost grows linearly with table size inside a transaction that holds a table-wide lock, so concurrent updaters wait longer.
- **Action**: Update only deposits whose balance changed, or batch the updates. Not needed for kata scale.

### 3. Low — Contract between port and adapter is only a KDoc
- **Where**: TimeDepositRepository.kt:12-13, ExposedTimeDepositRepository.kt:11,20
- **Problem**: "Locked until the transaction ends" holds only if the caller is inside inTransaction. Nothing enforces it. A call outside a transaction makes Exposed throw (no current transaction). If the lock were ever dropped, or the service switched to findAll(), the code would still compile and the tests could still pass without concurrency. Because findAll and findAllForUpdate both exist, the wrong one is a one-word mistake.
- **Why it matters**: A silent lost-update race the next time someone edits the service.
- **Action**: Make sure an IT exercises two concurrent updates (full mode: check that one exists and fails when forUpdate() is removed).

### 4. Info — Double round trip of the balance
- **Where**: ExposedTimeDepositRepository.kt:29,38
- **Problem**: DB numeric -> Double -> calculator (rounds with BigDecimal(Double) HALF_UP) -> BigDecimal.valueOf(Double).setScale(2). valueOf uses the shortest decimal string, so a value already rounded to 2 places comes back exactly. Balances above about 1e15 lose cents in the Double, and this is by design (hard constraint 2). I found no wrong behaviour in range.
- **Action**: Check the column precision in V1__create_time_deposit_tables.sql against the Double limit (full mode).

## Looked at and found sound
- Lock ordering by id before forUpdate removes the deadlock between concurrent updaters.
- An empty table gives empty lists, so there is nothing to lock or update.
- The update's where-clause is by primary key, and the SET touches only balance.
- The transaction wraps read, calculate and write as one unit, so it commits or rolls back together.
