# adversarial-architecture

Mode: full. Range: `c4ea358..HEAD` (working tree clean). Claims tested: `CLAUDE.md` "Architecture (hexagonal, single
module)" and `docs/ARCHITECTURE.md`. The package import graph was built mechanically from every `package`/`import`
line under `src/main/kotlin` (AST-free but exhaustive: 25 files, 0 skipped) and cycle-searched; Exposed's transaction
resolution was confirmed from the bytecode of `exposed-jdbc-1.4.0.jar`. No file in the checkout was modified.

Several candidate findings are already recorded as accepted in `docs/TRADEOFFS.md` (root package / `domain.interest`
mutual reference, no layering test, `Dispatchers.IO`, default credentials, duplicate plan types, single module).
Those are not re-litigated; findings 1, 2, 3 and 4 are new, and finding 1 corrects the recorded framing.

## Architectural claims that are fully honoured

Verified mechanically, not by impression:

- **Domain and application are plain Kotlin.** Zero matches for `exposed`, `io.ktor`, `kotlinx.serialization`,
  `javax.sql`, `hikari` or `flyway` under `domain/` and `application/`. The central claim of
  `ARCHITECTURE.md:26-27` holds exactly.
- **Adapters never know each other.** The graph contains no edge between `adapter.web` and `adapter.persistence`
  in either direction.
- **`Dispatchers.IO` is decided in the web adapter, and only there.** The only occurrences in main code are
  `adapter/web/TimeDepositRoutes.kt:21` and `:25`. No `runBlocking`, no `suspend` leaking inward.
- **`TransactionRunner` is the only application-level transaction entry point.** `transaction(` occurs once in main
  code, at `adapter/persistence/ExposedTransactionRunner.kt:12`. Flyway at `config/TimeDepositModule.kt:25` is the
  single other transaction opener and is documented as owning the schema.
- **Exposed tables and DTOs stay in their adapter.** `TimeDepositsTable`/`WithdrawalsTable` are `internal`
  (`Tables.kt:8,17`) and referenced only inside `adapter/persistence`; `@Serializable` appears only in
  `adapter/web`, and `toResponse()` is `internal`/`private`.
- **Wiring is manual and only in `config/`.** No package other than `org.ikigaidigital` (i.e. `Application.kt`)
  imports `config`, and no package other than `config` constructs an adapter.
- **No configuration sprawl.** `System.getenv` is read in exactly one place, `Application.kt:11-13`, and becomes a
  `DatabaseConfig` value object. Nothing else reads the environment or system properties.
- **No cycle-breaking tricks and no god objects.** There is not one function-level or lazy import in the codebase.
  484 lines of main code across 25 files; the largest is `TimeDepositCalculator.kt` at 46 lines. Every class has one
  responsibility; `GetAllTimeDepositsService` and `UpdateAllBalancesService` do nothing but orchestrate.
- **The `*Test`/`*IT` split works through plugin defaults.** `TimeDepositApiIT` matches no Surefire default pattern,
  so `make test` cannot pull Docker in.

The inward direction of the layering is, at *file* granularity, honoured without exception. The findings below are
about package granularity, hidden state and two invariants that are weaker than documented — not about the shape of
the dependency arrows between web, application and persistence, which is correct.

## Findings

### 1. Medium — `Application.kt`, not the pinned classes, is what makes the root package un-layerable

- **Where** `src/main/kotlin/org/ikigaidigital/Application.kt:5-6`; claims at `docs/ARCHITECTURE.md:33`,
  `docs/ARCHITECTURE.md:46` vs `:51`, `docs/TRADEOFFS.md` ("`TimeDeposit` and `TimeDepositCalculator` stay in the
  root package", "No layering test").
- **Problem** CONFIRMED. `TRADEOFFS.md` attributes the root-package tangle to the two pinned classes, i.e. to
  something unavoidable. The graph says otherwise. `Application.kt` adds `org.ikigaidigital -> config` and
  `org.ikigaidigital -> adapter.persistence`, which makes the root package simultaneously the innermost layer
  (Entities, `ARCHITECTURE.md:46`) and the outermost (Main, `ARCHITECTURE.md:51`). Cycle search over the package
  graph returns **31 distinct cycle paths**; deleting `Application.kt` from the graph and re-running returns
  exactly **2** — the unavoidable `org.ikigaidigital <-> domain.interest` pair. So 30 of 31 cycles come from one
  file whose location no hard constraint pins (constraints 1 and 2 cover `TimeDeposit` and `TimeDepositCalculator`
  only). The real upward reach is `domain.interest -> org.ikigaidigital -> config -> adapter.persistence -> Exposed`:
  at package granularity the domain transitively reaches the database.
- **Why it matters** This is the blocker for the ArchUnit/Konsist test `TRADEOFFS.md` names as "the first follow-up
  worth adding". Any package-granular rule of the form "`domain..` must not depend on `adapter..`" fails today, and
  it fails for a reason that has nothing to do with the assignment's constraints. The unavoidable residue
  (`org.ikigaidigital <-> domain.interest`) is one declared exception a rule can whitelist; the current 31 cannot be
  whitelisted without whitelisting the real violations too.
- **Action** Move `fun main()` to `config/` (or a `bootstrap/` package). The root package then holds only the two
  pinned domain classes, the graph becomes a DAG apart from one documented pair, and the layering test becomes
  writable. Note `README.md:60-67` documents `org.ikigaidigital.ApplicationKt` as the entry point, so the README
  command and `Makefile:22`'s sibling invocation move with it.

### 2. Medium — the outbound ports hide a shared-state dependency between the repositories and the transaction runner

- **Where** `application/port/outbound/TimeDepositRepository.kt:5-13`, `.../WithdrawalRepository.kt:5-7`,
  `.../TransactionRunner.kt:5`; implementations `adapter/persistence/ExposedTimeDepositRepository.kt:11-32`,
  `ExposedWithdrawalRepository.kt:7-11`, `ExposedTransactionRunner.kt:10-12`.
- **Problem** CONFIRMED from bytecode. `selectAll` is `selectAll(FieldSet)` and `update` is
  `update(T, Function0, Integer, Function2)` — neither takes a transaction. `Query.getTransaction()` is a protected
  ambient lookup, and `TransactionManager$Companion` exposes `getDefaultDatabase`/`setDefaultDatabase`,
  `registerManager`, `closeAndUnregister` and `current()`. So the repositories obtain their connection from global +
  thread-local state that `ExposedTransactionRunner` installs. Nothing in the three port signatures expresses this:
  `findAll()` is a nullary function that compiles and type-checks anywhere and throws only at runtime outside a
  transaction. The requirement survives as prose in two KDoc comments ("never called outside one") and in the port
  doc at `TimeDepositRepository.kt:12`, which has to mention transactions and locks to describe a method whose type
  mentions neither. Secondary: `Database.connect` (`ExposedTransactionRunner.kt:10`) mutates the global registry and
  `closeAndUnregister` is never called anywhere — `config/TimeDepositModule.kt:24` closes the Hikari pool on
  `ApplicationStopped` but leaves the Exposed registration behind, once per `timeDepositModule` call (four times in
  `TimeDepositApiIT`).
- **Why it matters** This is a dependency no diagram shows: `ARCHITECTURE.md:87-88` states it in prose, but the
  contract that would enforce it is absent. It is correct today only because `withContext(Dispatchers.IO)` runs the
  whole `inTransaction` block on one thread and `inTransaction(block: () -> T)` is non-suspending, so nothing can
  hop threads mid-transaction. Both of those are accidents of the current call site, not guarantees of the port. A
  future `async` inside a service, or a second `TransactionRunner` instance, silently changes which connection a
  repository uses. PLAUSIBLE that this misbehaves rather than throws, because the ambient lookup can find *a*
  transaction.
- **Action** Cheapest honest fix: state the precondition on the port itself (KDoc on the interface, not only the
  implementation) and add one integration test asserting that `ExposedTimeDepositRepository().findAll()` outside
  `inTransaction` fails loudly. Structural fix (larger): give `inTransaction` a context receiver or pass a unit-of-
  work handle the repositories require. Also call `TransactionManager.closeAndUnregister(database)` alongside
  `dataSource.close()`.

### 3. Medium — `toMoney()` is not the single money conversion point it claims to be

- **Where** `domain/Money.kt:6-10` ("this is the single conversion point"), `CLAUDE.md` ("Money outside the domain
  is `BigDecimal` with scale 2; convert with `Double.toMoney()` only"), violated at
  `adapter/web/TimeDepositResponses.kt:35`.
- **Problem** CONFIRMED. Withdrawal amounts never pass through `toMoney()`. The path is
  `withdrawals.amount NUMERIC(19,2)` -> `ExposedWithdrawalRepository.kt:17` -> `Withdrawal.amount: BigDecimal`
  (`domain/Withdrawal.kt:9`) -> `WithdrawalResponse.amount` via `amount = amount`. The scale-2 property of every
  amount the API emits therefore rests on the migration's column type
  (`V1__create_time_deposit_tables.sql`), not on the stated conversion point. Nothing checks it: the contract
  declares `type: number` with no `multipleOf` (`openapi/time-deposits.yaml:79-80`), so
  `swagger-request-validator` would pass `500.0000`, and `BigDecimalAsNumberSerializer` prints whatever scale it is
  handed (`toPlainString()`, line 23).
- **Why it matters** Two money fields on the same response have two different and unequal guarantees, and the
  weaker one is invisible. Widen the column to `NUMERIC(19,4)`, or source withdrawals from anywhere but this table,
  and `balance` keeps two decimals while `amount` silently does not. The documented invariant is doing less work
  than a reader of `Money.kt:8` would assume.
- **Action** Either apply the scale at the boundary (`amount = amount.setScale(2, HALF_UP)` in `toResponse()`, or a
  `BigDecimal.toMoney()` overload beside the `Double` one) or narrow the claim in `Money.kt:8` and `CLAUDE.md` to
  "the single conversion point *from `Double`*", and say that `BigDecimal` money is scale-2 by database contract.
  A `multipleOf: 0.01` in the contract would let the existing integration tests catch it.

### 4. Medium — every structural rule in CLAUDE.md is enforced by review only; the gate has no linter, coverage floor or dependency audit

- **Where** `pom.xml:150-209` (plugins: kotlin, surefire, failsafe, exec — nothing else), `Makefile:3`
  (`help setup test verify run`), `.githooks/pre-commit:7`, `.githooks/pre-push:29`.
- **Problem** CONFIRMED. There is no `check` target; the hooks call `make test`/`make verify` directly. The build
  has no formatter or linter in check mode (no ktlint/detekt), no type-strictness flag
  (`allWarningsAsErrors`/explicit API mode are not set, so the strictest setting the project can hold is not
  taken), no coverage floor (no JaCoCo), no dependency or vulnerability audit (no `dependency:analyze`, no OWASP
  check), and no layering tool. So the rules in the "Code style" and "Architecture" sections of `CLAUDE.md` — no
  magic numbers, no unused code, inward-only dependencies, no Ktor/Exposed in the domain — have no mechanical
  guard at all.
- **Why it matters** `TRADEOFFS.md` records "No layering test" as an accepted gap, but the gap is wider than that
  one entry: the standing project rules for a commit gate name a linter, a coverage floor and a dependency audit as
  required rows, and none of the three exists. The architecture is currently clean (see the honoured list), which
  is exactly when the enforcement is cheapest to add and hardest to justify adding — and the only thing standing
  between it and drift is that every change so far has been reviewed by hand.
- **Action** Add a `make check` target that `pre-commit`/`pre-push`/CI all call, and put at least `allWarningsAsErrors`
  plus `mvn dependency:analyze` in it today (both are free). Then Konsist or ArchUnit for the layering rule — after
  finding 1, or it cannot express the rule. Record the remaining omissions in `TRADEOFFS.md` with reasons, the way
  "No layering test" already is.

### 5. Low — the ARCHITECTURE.md package table is wrong in three of its nine rows

- **Where** `docs/ARCHITECTURE.md:33`, `:34`, `:38`.
- **Problem** CONFIRMED against the graph. Row `org.ikigaidigital` says "Depends on: domain"; it also depends on
  `adapter.persistence` and `config` and imports Ktor (`Application.kt:3-6`). Row `domain` says "Depends on:
  nothing"; `domain/interest` imports `org.ikigaidigital.TimeDeposit` in all four of its files
  (`InterestPolicy.kt:3`, `BasicInterestPolicy.kt:3`, `PremiumInterestPolicy.kt:3`, `StudentInterestPolicy.kt:3`).
  Row `adapter/web` says "Depends on: inbound ports"; it also depends on `domain`
  (`TimeDepositResponses.kt:5-6`). The diagram at `:9-21` likewise omits the `adapter/web -> domain` edge.
- **Why it matters** This table is the most likely input to the layering test and the first thing a reviewer
  compares the code against. Two of the three errors understate real edges, so a rule written from the table would
  fail on correct code; the `domain` row contradicts `TRADEOFFS.md`, which does acknowledge the mutual reference.
- **Action** Correct the three cells: root package depends on `domain`, `config`, `adapter/persistence`, Ktor (or,
  after finding 1, on `domain` only); `domain` depends on "the pinned root-package classes"; `adapter/web` depends
  on "inbound ports, domain". Add the `adapter/web -> domain` arrow to the diagram.

### 6. Low — the composition root migrates the schema, and that ordering leaks into the tests

- **Where** `config/TimeDepositModule.kt:23-25` and `:34-37`; leak visible at
  `src/test/kotlin/.../TimeDepositApiIT.kt:110-112`.
- **Problem** CONFIRMED. `timeDepositModule` is not only wiring: it creates a connection pool, **runs Flyway**, and
  configures two web concerns (the JSON plugin and the Swagger path plus the contract filename). The migration side
  effect is the one that escapes: because the schema appears only once the module has been installed *and started*,
  `TimeDepositApiIT` must call `startApplication()` before inserting data, with a comment saying exactly that
  ("runs the Flyway migrations before the test data goes in"). `ARCHITECTURE.md:40` admits config "creates
  everything and installs the routes and Swagger UI", so the web part is documented; the migrate-on-install part
  is not.
- **Why it matters** A composition root that performs I/O cannot be composed without performing that I/O. Every
  current caller wants it, so the cost today is one ordering constraint on tests — but it is a responsibility
  (schema lifecycle) sitting in the one file whose documented job is "wires adapters to use cases", and the test
  comment is the evidence that a caller had to learn about it.
- **Action** Either accept it explicitly in `TRADEOFFS.md` ("the composition root migrates on start; tests must
  start the application first") or lift `migrate(...)` to the two entry points (`Application.kt`,
  `LocalApplication.kt`) that already own the `DatabaseConfig`, leaving the module to wiring.

### 7. Low — `InterestPolicy` is public but has a precondition only the calculator satisfies

- **Where** `domain/interest/InterestPolicy.kt:8-10,12-16`; enforcement at `TimeDepositCalculator.kt:31`.
- **Problem** CONFIRMED, and partly recorded in `TRADEOFFS.md` ("The 30-day rule lives in the calculator"). The
  interface is `public` while all three implementations are `internal`, so the only extension point reachable from
  outside the module is the one carrying an unstated precondition: `monthlyInterest(deposit)` returns a wrong
  answer for `days <= 30` unless the caller filters first. The KDoc says policies "are not meant to be called
  directly" — a protocol whose documentation asks callers not to use it. `TimeDepositCalculatorTest` implements the
  interface directly, so the extension path is real, not hypothetical.
- **Why it matters** `CLAUDE.md` says a new interest plan is a new `InterestPolicy`. An implementer following that
  instruction from outside the module sees a `TimeDeposit` parameter with a `days` field and no hint that `days` has
  already been screened. The three shipped implementations have identical shape, so the protocol itself is honoured;
  it is the ordering assumption that is smuggled in.
- **Action** Narrow the parameter to what a policy may legitimately use (`balance`, `days`) or make the interface
  `internal` and expose plan registration through a function in the root package, so the precondition cannot be
  reached unguarded. If neither, say in the KDoc that the contract is "called only for `days > 30`".

### 8. Low — `findAllForUpdate` puts pessimistic locking in the vocabulary of an application port

- **Where** `application/port/outbound/TimeDepositRepository.kt:12-13`, chosen at
  `application/service/UpdateAllBalancesService.kt:15`; claim at `docs/ARCHITECTURE.md:87`.
- **Problem** CONFIRMED but defensible. `ARCHITECTURE.md:87` says "the application layer does not know Exposed",
  which is true — but the application layer does know about row locking: the service has to pick `findAllForUpdate()`
  over `findAll()`, and the port's own doc comment has to explain locks and transaction lifetime to describe the
  difference. Two methods differing only by an isolation strategy is a database capability surfacing in the port.
- **Why it matters** Small and arguably correct — "this use case must serialise against concurrent updates" is a
  business requirement, not a technology detail, and the alternative (an in-port isolation-level enum, or doing it
  inside `updateBalances`) is worse. Worth recording rather than changing, because the doc's phrasing invites the
  reader to expect a stronger separation than exists.
- **Action** Reword `ARCHITECTURE.md:87` to "does not know Exposed, but does declare that the balance update needs
  serialised access", or rename to express intent rather than mechanism (`findAllForSerialisedUpdate`).

### 9. Low — `BigDecimalAsNumberSerializer.deserialize` is unreachable

- **Where** `adapter/web/BigDecimalAsNumberSerializer.kt:25-26`.
- **Problem** CONFIRMED. Neither endpoint has a request body (`openapi/time-deposits.yaml` contains no
  `requestBody`), and no test decodes into `TimeDepositResponse`/`WithdrawalResponse` — `TimeDepositApiIT` compares
  with `Json.parseToJsonElement` on raw text. Nothing in main or test code ever deserializes a `BigDecimal` through
  this serializer. `CLAUDE.md` says "no unused code".
- **Why it matters** Minimal — `KSerializer` requires the member, so it cannot simply be deleted. The point is that
  the line is untested and unreachable while looking like live behaviour, and it would be the first thing to break
  silently if a request body were ever added.
- **Action** Leave it and say so, or make the unreachability explicit:
  `override fun deserialize(decoder: Decoder): Nothing = throw UnsupportedOperationException("responses only")`.

### 10. Low — the lock test observes a cluster-wide condition

- **Where** `src/test/kotlin/.../TimeDepositApiIT.kt:158-165`.
- **Problem** CONFIRMED, and already recorded in `TRADEOFFS.md` ("The lock test waits for any blocked session").
  Noted here only for its architectural shape: `SELECT count(*) FROM pg_stat_activity WHERE wait_event_type =
  'Lock'` is unfiltered by database, pid or relation, so the test's synchronisation reads global server state
  rather than its own. The four tests in the class share one container (`:30`) and reset it with `TRUNCATE` at
  `:112`, so correctness depends on tests not running in parallel.
- **Why it matters** The accepted reasoning ("only the test's own sessions run against the test database") is
  sound today and would stop being sound the moment Failsafe gets a parallel configuration or a second IT class
  shares the container. It is a latent coupling to a global, not a current defect.
- **Action** Filter the query by `datname = current_database()` and `pid <> pg_backend_pid()` — a one-line change
  that makes the existing assumption explicit instead of ambient.
