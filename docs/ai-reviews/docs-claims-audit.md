# docs-claims-audit (full) - c4ea358..HEAD

### 1. Medium - Lock test is described as proving something it does not test
- **Where**: README.md ("One test proves that two overlapping balance updates cannot lose a month"); docs/PRODUCT.md ("Two overlapping calls run one after the other, so each adds its month"); TimeDepositApiIT.kt:92.
- **Problem**: the test holds `FOR UPDATE` and changes a balance from a raw second JDBC connection, then fires ONE balance update. It proves the update waits for a concurrent writer, not that two balance updates serialise. ARCHITECTURE.md and TRADEOFFS.md describe it correctly ("holds row locks from a second connection").
- **Why it matters**: the headline concurrency guarantee is worded as tested when it is inferred.
- **Action**: reword README to "waits for a concurrent change and builds on it".

### 2. Medium - "Exactly two endpoints" does not account for the Swagger UI route
- **Where**: CLAUDE.md hard constraint 3 ("Exactly two HTTP endpoints ... No others"); README intro; PRODUCT.md API section.
- **Problem**: `config/TimeDepositModule.kt` also installs `swaggerUI(path = "swagger", ...)` (serves /swagger and the contract file). README and ARCHITECTURE mention Swagger UI, but CLAUDE.md never exempts it and the "exactly two" sentences are unqualified.
- **Why it matters**: the rule is stated as absolute but the code already breaks the literal reading; an AI or reader cannot tell if the route is allowed.
- **Action**: say "two API endpoints, plus Swagger UI at /swagger" in CLAUDE.md, README and PRODUCT.md.

### 3. Medium - docs-claims-audit model: docs say Opus for full mode
- **Where**: docs/AI_USAGE.md ("/quality-gate full: every relevant agent on Opus"; TRADEOFFS "Opus is kept for the one thorough review").
- **Problem**: `.claude/agents/docs-claims-audit.md` declares `model: sonnet`, effort medium; quality-gate.md uses the agent's own model in full mode, so this agent runs on Sonnet.
- **Action**: say "on each agent's own model (Opus except docs-claims-audit)" or change the agent.

### 4. Low - TRADEOFFS: integration data "on the paying side of each boundary (31, 46 and 366 days)"
- **Where**: docs/TRADEOFFS.md, Testing.
- **Problem**: student at 366 days does NOT pay (the IT asserts 1000.00 unchanged). 366 is the non-paying side.
- **Action**: "on one side of each boundary" / "31, 46 (paying) and 366 (not paying)".

### 5. Low - AI_USAGE Claude Code version is stale/unverifiable
- **Where**: docs/AI_USAGE.md ("Claude Code CLI (v2.1.177)").
- **Problem**: `claude --version` on this machine reports 2.1.319. Nothing in the repo pins a version.
- **Action**: drop the number or say "tested with".

### 6. Low - "The same files live in ~/.claude/"
- **Where**: docs/AI_USAGE.md, harness section.
- **Problem**: `~/.claude/agents` has the seven agents (plus others, e.g. adversarial-style), but `~/.claude/commands` has only quality-gate.md, not review-step.md. Not checkable by a reader of the repo.
- **Action**: remove or qualify.

### 7. Low - Dependency table says `domain` depends on "nothing"
- **Where**: docs/ARCHITECTURE.md Packages table; CLAUDE.md "dependencies point inwards only".
- **Problem**: `domain/interest/*` imports `org.ikigaidigital.TimeDeposit` (root package) and the root package imports domain/interest. TRADEOFFS acknowledges this, ARCHITECTURE does not. `org.ikigaidigital` is also listed as depending only on "domain" though `Application.kt` depends on `config`.
- **Action**: footnote the root-package cycle in ARCHITECTURE.

### 8. Low - Undocumented behaviour a newcomer would trip on
- Production `main()` silently falls back to `jdbc:postgresql://localhost:5432/timedeposits` with `timedeposits/timedeposits` when env vars are unset; README says only that it "reads" the variables (TRADEOFFS records it).
- The `R__demo_data.sql` seed is a Flyway repeatable migration in test resources with hard-coded 2026 dates; README/ARCHITECTURE mention the folder but not the mechanism.
- `toMoney()` uses `BigDecimal.valueOf` while the calculator uses `BigDecimal(Double)`; the two are deliberately different, documented only partly (TRADEOFFS) and not in ARCHITECTURE "Persistence".
- `/swagger` is not exercised by any test (no test requests it).

### Unverifiable
- AI_USAGE: "200,000 random inputs", "about 2.5 million", five surviving mutants, 8% CPU, "15 minutes" - no artefact in the repo besides review reports (not re-run).
- README "Swagger UI shows 10000 where the API returns 10000.00" - not run (no app start allowed).

## Checked and accurate
- All four make targets exist and match Makefile; `make test T=...` supported; `.DEFAULT_GOAL` help.
- Versions: Kotlin 2.3.21, Ktor 3.5.0 (Netty), Exposed 1.4.0, Java 17, Flyway, Hikari, Testcontainers, swagger-request-validator, exec-maven-plugin present.
- Interest rules in PRODUCT.md and README table recomputed against TimeDepositCalculator and the three policies: rates 1/3/5%, >30 days, student <=365, premium >45, unknown plan 0, all six example rows (0.83, 2.50, 4.17, 8.33, 50.00, 416.67) correct; case-sensitive; last policy wins; `defaultPolicies()` and constructor injection exist.
- Seed data (6 deposits, deposits 1/3/6 have withdrawals) matches README table.
- Endpoints: GET /time-deposits and POST /time-deposits/balance-updates (204) match the OpenAPI file and routes; `additionalProperties: false` present; Dispatchers.IO in the web adapter; row lock `forUpdate()` ordered by id; two queries for GET; one transaction via `TransactionRunner`; Hikari closed on ApplicationStopped.
- Migration creates `time_deposits`, `withdrawals`, NUMERIC(19,2), FK; `Double.toMoney()` lives in domain/Money.kt.
- Surefire/Failsafe default includes give `*Test` / `*IT`; hooks: pre-commit runs `make test` only when `.githooks/code-paths` paths are staged; pre-push runs `make verify`, then `claude -p "/quality-gate auto ..." --model sonnet`, 180 s default timeout, prints/needs `QUALITY-GATE: DONE`, stops once when reports are uncommitted, `SKIP_AI_GATE`, `--no-verify`; docs-only pushes skip (code-paths has no docs); `.githooks` and `.claude` are in code-paths.
- Seven agents present with the names listed; quality-gate modes push/full and class-to-agent table match AI_USAGE.
- No Ktor/Exposed/serialization imports in domain/ or application/ (grep).
- Lock-test numbers (1000.83 vs 2001.67) match the test comment; `postgres:17-alpine` matches.
