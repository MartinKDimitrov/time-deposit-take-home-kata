# XA Bank Time Deposit API

A Kotlin service for the Ikigai Digital time deposit kata. It keeps time deposits and their withdrawals in
PostgreSQL and exposes exactly two API endpoints: one lists all deposits, the other adds one month of interest to every
balance. Swagger UI at `/swagger` serves the contract. The interest rules live in the original `TimeDepositCalculator.updateBalance`, whose behaviour is unchanged.

**Stack:** Kotlin 2.3, Java 17, Ktor 3 (Netty), Exposed, PostgreSQL with Flyway, an OpenAPI 3 contract served by
Swagger UI, Testcontainers, Maven (wrapper included).

The original assignment is in [`docs/ASSIGNMENT.md`](docs/ASSIGNMENT.md).

## Prerequisites

- JDK 17 or newer
- Docker running (for `make verify` and `make run`)
- `make`

Maven does not need to be installed: `./mvnw` downloads the pinned version.

## Run it and try the endpoints with Swagger

```bash
make run
```

This starts PostgreSQL in Docker (Testcontainers), applies the schema, loads six demo deposits and serves the API on
port 8080. The demo data is a Flyway repeatable migration in `src/test/resources/db/seed`, so it never reaches a
production database. Stop it with Ctrl+C; the database container is removed automatically.

Open **http://localhost:8080/swagger**. The page shows the contract
([`src/main/resources/openapi/time-deposits.yaml`](src/main/resources/openapi/time-deposits.yaml)) with both
endpoints.

1. **List the deposits.** Expand `GET /time-deposits`, click **Try it out**, then **Execute**. The response is `200`
   with six deposits ordered by id; deposits 1, 3 and 6 have withdrawals.
2. **Apply one month of interest.** Expand `POST /time-deposits/balance-updates`, click **Try it out**, then
   **Execute**. The response is `204` with no body.
3. **List them again** and compare the balances:

   | id | plan | days | before | after | why |
   | --- | --- | --- | --- | --- | --- |
   | 1 | basic | 45 | 10000.00 | 10008.33 | 1% a year |
   | 2 | basic | 20 | 5000.00 | 5000.00 | first 30 days earn nothing |
   | 3 | student | 200 | 20000.00 | 20050.00 | 3% a year |
   | 4 | student | 400 | 15000.00 | 15000.00 | students earn nothing after one year |
   | 5 | premium | 40 | 50000.00 | 50000.00 | premium starts after 45 days |
   | 6 | premium | 90 | 100000.00 | 100416.67 | 5% a year |

Every further POST adds another month. The endpoint is deliberately not idempotent.

Swagger UI re-formats JSON numbers, so it shows `10000` where the API returns `10000.00`. The raw response keeps two
decimals:

```bash
curl -s http://localhost:8080/time-deposits
curl -s -X POST -o /dev/null -w "%{http_code}\n" http://localhost:8080/time-deposits/balance-updates
```

### Against your own PostgreSQL

`make run` is for trying the API. The production entry point, `org.ikigaidigital.ApplicationKt`, reads the database
from `DB_URL`, `DB_USER` and `DB_PASSWORD`, runs the Flyway migrations and starts with an empty database. Without the variables it falls back to
`jdbc:postgresql://localhost:5432/timedeposits` with user and password `timedeposits`, which matches this container:

```bash
docker run -d --name timedeposits -p 5432:5432 \
  -e POSTGRES_DB=timedeposits -e POSTGRES_USER=timedeposits -e POSTGRES_PASSWORD=timedeposits postgres:17-alpine
DB_URL=jdbc:postgresql://localhost:5432/timedeposits DB_USER=timedeposits DB_PASSWORD=timedeposits \
  ./mvnw -q compile exec:java -Dexec.mainClass=org.ikigaidigital.ApplicationKt
```

There is no endpoint to create deposits (the assignment allows exactly two), so data is inserted into the
`time_deposits` and `withdrawals` tables directly.

## Build and test

| Command | What it does |
| --- | --- |
| `make test` | Compiles and runs the unit tests (`*Test`). No Docker, a few seconds. One class: `make test T=TimeDepositCalculatorTest` |
| `make verify` | Unit tests plus integration tests (`*IT`) against PostgreSQL in Docker |
| `make run` | Starts the API with demo data (see above) |
| `make setup` | Enables the git hooks of this repository (once per clone) |

- **Unit tests** pin the behaviour of `updateBalance`: every plan boundary (30/31, 45/46 and 365/366 days), the
  rounding mode and the arithmetic order of the original code, unknown and wrongly cased plan types, and adding a new
  plan.
- **Integration tests** go through HTTP, the use cases and Exposed to a real PostgreSQL. Every response is validated
  against the OpenAPI contract, so the code and the contract cannot drift apart. Two tests cover concurrent updates:
  one holds the row locks from a second connection and checks that an update waits and builds on the change; the
  other runs two updates at once and expects both months of interest.

## Project layout

```
src/main/kotlin/org/ikigaidigital
  TimeDeposit, TimeDepositCalculator   the shared classes from the assignment (package kept)
  domain/                              Withdrawal, money conversion, interest/*Policy
  application/port/inbound|outbound    use case and repository interfaces
  application/service                  use case implementations
  adapter/web                          Ktor routes and response DTOs
  adapter/persistence                  Exposed tables and repositories, Hikari pool, Flyway
  config/                              composition root: wires adapters to use cases
src/main/resources                     openapi/time-deposits.yaml, db/migration, logback.xml
src/test                               unit tests, integration tests, LocalApplication (make run), demo data
```

## Documentation

| Document | Contents |
| --- | --- |
| [`docs/PRODUCT.md`](docs/PRODUCT.md) | Business rules, API behaviour and the assumptions made |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Hexagonal structure, request flow, transactions and locking, tests |
| [`docs/TRADEOFFS.md`](docs/TRADEOFFS.md) | Decisions with their reasons and what they give up, accepted review findings |
| [`docs/AI_USAGE.md`](docs/AI_USAGE.md) | The AI setup, how to reproduce it, and what was AI-assisted |
| [`docs/ai-reviews/`](docs/ai-reviews/) | Latest reports of the AI review agents |
| [`CLAUDE.md`](CLAUDE.md) | Project rules for AI assistants |
