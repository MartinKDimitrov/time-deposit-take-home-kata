# Product

What the service does, from the point of view of the bank. How it is built is in
[`ARCHITECTURE.md`](ARCHITECTURE.md); why it is built that way is in [`TRADEOFFS.md`](TRADEOFFS.md).

## Time deposits

A time deposit has an id, a plan type, a balance and an age in days. It can have withdrawals, each with an id, an
amount and a date. Deposits and withdrawals are stored in PostgreSQL (`time_deposits`, `withdrawals`).

## Monthly interest

One balance update adds one month of interest to every deposit:

```
monthly interest = balance × annual rate ÷ 12, rounded half-up to whole cents
```

| Plan | Annual rate | Earns interest when |
| --- | --- | --- |
| `basic` | 1% | the deposit is older than 30 days |
| `student` | 3% | the deposit is older than 30 days and at most 365 days old |
| `premium` | 5% | the deposit is older than 45 days |
| any other | – | never |

Examples (one update):

| Deposit | Interest | New balance |
| --- | --- | --- |
| basic, 1000.00, 31 days | 0.83 | 1000.83 |
| basic, 1000.00, 30 days | 0.00 | 1000.00 |
| student, 1000.00, 365 days | 2.50 | 1002.50 |
| student, 1000.00, 366 days | 0.00 | 1000.00 |
| premium, 1000.00, 45 days | 0.00 | 1000.00 |
| premium, 1000.00, 46 days | 4.17 | 1004.17 |

These numbers come from the original `TimeDepositCalculator.updateBalance`, which the assignment says is correct.
Its behaviour is kept exactly, including the `Double` arithmetic and the rounding, and pinned by tests.

## API

The contract is [`src/main/resources/openapi/time-deposits.yaml`](../src/main/resources/openapi/time-deposits.yaml),
served by Swagger UI at `http://localhost:8080/swagger`. There are exactly two API endpoints; Swagger UI is the only
other route.

### `GET /time-deposits`

Returns `200` with every deposit, ordered by id, each with its withdrawals:

```json
[
  {
    "id": 1,
    "planType": "basic",
    "balance": 10000.00,
    "days": 45,
    "withdrawals": [{ "id": 1, "amount": 500.00, "date": "2026-09-15" }]
  }
]
```

Money is a JSON number with two decimals. Dates are ISO-8601 (`yyyy-MM-dd`). A deposit without withdrawals has
`"withdrawals": []`.

### `POST /time-deposits/balance-updates`

Adds one month of interest to every deposit and returns `204` with no body.

- **Not idempotent.** Every call adds another month. Calling it twice adds two months.
- **All or nothing.** All balances are updated in one database transaction.
- **Safe when called concurrently.** Two overlapping calls run one after the other, so each adds its month and none is
  lost.

## Assumptions

The assignment leaves these points open. Each choice is the simplest reading that keeps the original behaviour.

| # | Assumption | Why |
| --- | --- | --- |
| A1 | The interest-free first 30 days apply to every plan, including plans added later. | One rule for all plans is the simplest reading of "for any existing plans". |
| A2 | Plan types are matched case-sensitively; an unknown plan type earns no interest. | This is what the original code does. |
| A3 | "No interest after 1 year" means a student deposit earns interest while it is at most 365 days old. | The original code pays while `days < 366`. |
| A4 | A balance update does not change `days`. | The original calculator never changes it and the assignment defines no clock, so the age of a deposit is maintained outside this service. Consequence: nothing in the service advances `days`, so a deposit only crosses a boundary (30, 45 or 365 days) when that outside writer updates it. A service that owned the age would store an opening date and derive `days` from a clock port. |
| A5 | Withdrawals are history only: they do not change the balance or the interest. | The stored balance is taken as the current balance; the original calculator ignores withdrawals. |
| A6 | The update endpoint is `POST /time-deposits/balance-updates` with `204 No Content`. | Each call creates a new effect (one more month), so it is not a `PUT`; there is nothing useful to return. |
| A7 | `withdrawals` in the response is the list of the deposit's withdrawals (`id`, `amount`, `date`), without `timeDepositId`. | The assignment names the field but not its shape; a nested list is the most useful reading, and the deposit id is implied by the nesting. |
| A8 | The plan percentages are annual rates; one update adds one twelfth. | The assignment says "monthly interest" and "1% interest" without a period; the original code computes `balance * rate / 12`, and its behaviour is pinned. |

## Out of scope

As the assignment allows, the service does not validate input or handle errors beyond what the frameworks do. There
is no authentication, paging, or endpoint to create deposits or withdrawals: data is inserted into the database
directly. `make run` loads demo data from the test resources, and the integration tests insert their own.
