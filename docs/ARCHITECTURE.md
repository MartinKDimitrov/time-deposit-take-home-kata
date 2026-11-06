# Architecture

Hexagonal architecture (ports and adapters) in a single Maven module. The business rules sit in the middle and know
nothing about HTTP or the database; adapters on the outside translate between those technologies and the use cases.

```
   HTTP                                                            PostgreSQL
    │                                                                   ▲
┌───▼─────────────────┐   ┌─────────────────────────────┐   ┌─────────┴───────────┐
│ adapter/web         │   │ application                 │   │ adapter/persistence │
│ Ktor routes, DTOs   ├──►│ port/inbound  use cases     │◄──┤ Exposed tables,     │
│                     │   │ service       their logic   │   │ repositories,       │
│                     │   │ port/outbound repositories, │   │ transaction runner  │
│                     │   │               transactions  │   │                     │
└─────────────────────┘   └──────────────┬──────────────┘   └─────────────────────┘
                                         ▼
                          ┌─────────────────────────────┐
                          │ domain                      │
                          │ TimeDeposit, Calculator,    │
                          │ interest/*Policy, Withdrawal│
                          └─────────────────────────────┘

config/ (composition root) creates the adapters and wires them to the use cases.
```

Arrows point in the direction of the dependency. Nothing in `domain/` or `application/` imports Ktor, Exposed or
kotlinx.serialization.

## Packages

| Package | Contents | Depends on |
| --- | --- | --- |
| `org.ikigaidigital` | `TimeDeposit`, `TimeDepositCalculator` – the shared classes, kept in their original package; `Application.kt` (entry point) | domain |
| `domain` | `Withdrawal`, `Double.toMoney()`, `interest/InterestPolicy` and one policy per plan | nothing |
| `application/port/inbound` | `GetAllTimeDepositsUseCase`, `UpdateAllBalancesUseCase` | domain |
| `application/port/outbound` | `TimeDepositRepository`, `WithdrawalRepository`, `TransactionRunner` | domain |
| `application/service` | the two use case implementations | ports, domain |
| `adapter/web` | routes, response DTOs, mapping, the money serializer | inbound ports |
| `adapter/persistence` | Exposed tables, repositories, `ExposedTransactionRunner`, Hikari pool and Flyway | outbound ports |
| `config` | `timeDepositModule`: creates everything and installs the routes and Swagger UI | everything |

For readers who think in Clean Architecture terms, the same structure maps one to one:

| Clean Architecture | Here |
| --- | --- |
| Entities | `domain/`, `TimeDeposit`, `TimeDepositCalculator` |
| Use cases | `application/service` behind `application/port/inbound` |
| Gateways (boundaries) | `application/port/outbound` |
| Interface adapters | `adapter/web`, `adapter/persistence` |
| Frameworks and drivers | Ktor, Exposed, PostgreSQL |
| Main | `config/` and `Application.kt` |

## Interest rules

`TimeDepositCalculator` keeps its public API: the no-arg constructor and `updateBalance(List<TimeDeposit>)`. Inside,
each plan is an `InterestPolicy` (strategy pattern), looked up by plan type:

- the calculator applies the rule shared by every plan (no interest in the first 30 days), asks the policy for the
  monthly interest, and rounds it to cents exactly as the original code did;
- a policy holds only its own rate and limits (`BasicInterestPolicy`, `StudentInterestPolicy`,
  `PremiumInterestPolicy`).

A new plan is a new `InterestPolicy` class, added to `TimeDepositCalculator.defaultPolicies()` or passed to the
calculator's constructor. The calculator does not change.

Domain policies are classes, not ports: a port is an interface the application needs from the outside world (the
database, a clock). Interest rules are the business itself, so they live in the domain.

## Request flow

**`GET /time-deposits`**

1. The route switches to `Dispatchers.IO` (JDBC blocks) and calls `GetAllTimeDepositsUseCase`.
2. The service opens one transaction through `TransactionRunner`, loads all deposits and all withdrawals (two
   queries, no N+1), and groups the withdrawals by deposit.
3. The route maps the result to response DTOs; money becomes a `BigDecimal` with two decimals through `toMoney()`.

**`POST /time-deposits/balance-updates`**

1. The route switches to `Dispatchers.IO` and calls `UpdateAllBalancesUseCase`.
2. In one transaction the service loads all deposits **with row locks** (`SELECT … FOR UPDATE`, in id order), passes
   them to `TimeDepositCalculator.updateBalance`, and writes the new balances back.
3. The route responds `204`.

### Transactions and locking

Use cases control transactions through the `TransactionRunner` port, so the application layer does not know Exposed.
The repositories join the transaction opened by `ExposedTransactionRunner`.

The balance update reads, computes and writes absolute balances. Without locks, two overlapping updates would read the
same balances and one month of interest would be lost. `findAllForUpdate()` locks the rows until the transaction ends,
so a second update waits and then builds on the first. Locking in id order keeps the lock order the same for every
caller, so two updates cannot deadlock.

## Persistence

- **Flyway owns the schema** (`src/main/resources/db/migration`); Exposed only maps it. Tables are `time_deposits`
  and `withdrawals` with snake_case columns, money as `NUMERIC(19,2)`, and a foreign key from withdrawals to deposits.
- **Money:** the shared `TimeDeposit` keeps its balance as `Double`. At the database boundary it is converted with
  `toMoney()` (two decimals, half-up) on write and `toDouble()` on read.
- **Connections:** HikariCP pool, closed when the application stops.

## Contract first

The OpenAPI contract is written first and is the single source of truth. Ktor has no generator that produces only
interfaces, so the guarantee comes from the tests instead: every integration test validates the response against the
contract (`swagger-request-validator`), with `additionalProperties: false`, so an extra, missing or mistyped field
fails the build. Swagger UI serves the same file.

## Tests

| Level | What | How |
| --- | --- | --- |
| Unit (`*Test`, `make test`) | `updateBalance` behaviour | Characterization tests written against the original code before the refactoring: plan boundaries, rounding mode, arithmetic order, plan type matching, extensibility |
| Integration (`*IT`, `make verify`) | HTTP → use cases → Exposed → PostgreSQL | Ktor `testApplication` with PostgreSQL in Testcontainers; full JSON comparison and contract validation; a lock test that holds row locks from a second connection |

There are no mocks: the domain has no dependencies, and the integration tests use the real database.

`make run` uses the same Testcontainers PostgreSQL, with demo data from `src/test/resources/db/seed`.
