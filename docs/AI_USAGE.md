# AI usage

How AI was used to build this service, how to reproduce the setup, and which parts were AI-assisted.

## Tools

| Tool | Used for |
| --- | --- |
| **Claude** (Anthropic, Opus models) in the Claude desktop app | Pair work in a conversation: analysing the assignment, planning, a step-by-step implementation guide with proposed code for each step, discussed and adjusted together before it was typed, the quality tooling, the documentation, triage of review findings. Through the desktop app it could read the repository folder, check staged changes and write the tooling and documentation files. |
| **Claude Code** CLI (2.1; last run with 2.1.319) | Runs the review agents headless (`claude -p`): from the pre-push hook, and once for `/quality-gate full` before handover. Models are chosen by alias: `sonnet` for the quick review on push, because it is faster and cheaper for a review that runs many times a day; `opus` for the thorough review that runs once before handover (the docs audit runs on `sonnet` there too). |
| IntelliJ IDEA | Writing, running and debugging the code. |

## The harness

Everything that defines the AI setup is in the repository:

| File | Role |
| --- | --- |
| [`CLAUDE.md`](../CLAUDE.md) | Project rules every AI session reads first: context, `make` commands, hard constraints (shared class untouched, `updateBalance` unchanged, exactly two endpoints, contract first, no error handling), architecture and dependency rules, code style, test rules, workflow |
| [`.claude/agents/`](../.claude/agents/) | Seven review agents, each a focused prompt with model, effort and turn limit: `adversarial-code`, `adversarial-architecture`, `test-quality`, `regression-hunt`, `verify-findings-closed`, `spec-vs-plan`, `docs-claims-audit`. A shared "Execution rules" section keeps them read-only, isolates any build in a temporary git worktree, sets the budget per mode and bounds the output (at most 10 findings, each with location, problem, impact and action) |
| [`.claude/commands/quality-gate.md`](../.claude/commands/quality-gate.md) | `/quality-gate`: classifies a commit range by what changed (spec, refactor, logic, structure, tests, docs, fixes), picks the agents for each class, runs them in parallel and summarises. Two modes: `push` and `full` |
| [`.claude/commands/review-step.md`](../.claude/commands/review-step.md) | `/review-step`: reviews uncommitted changes against `CLAUDE.md` before a commit (interactive use) |
| [`.githooks/`](../.githooks/) | `pre-commit` runs `make test`; `pre-push` runs `make verify` and then the AI gate; `code-paths` lists the paths that trigger them |
| [`Makefile`](../Makefile) | The single entry point for build and test commands, used by humans, hooks and agents alike |

The agents and commands are written to be project-independent: everything project-specific comes from `CLAUDE.md`.
They can be copied to `~/.claude/` to use them in other repositories.

### When what runs

| Moment | Automatic | Time |
| --- | --- | --- |
| `git commit` (code or build files staged) | `make test` | seconds |
| `git push` (code or build files changed) | `make verify`, then `claude -p "/quality-gate auto <range>"` in **push mode**: only the quick agents (`adversarial-code`, `test-quality`, `verify-findings-closed`, chosen by what changed), read-only, on Sonnet, in parallel, stopped after 3 minutes. If reports were written the push stops once, so they can be read, acted on and committed. | 1–2 minutes |
| Handover | `/quality-gate full <base>` by hand: every relevant agent on its own model (Opus; Sonnet for the docs audit), allowed to prove findings by running code (mutations, differential tests) | about 20 minutes, run once |

Only commits not yet reviewed are reviewed on the next push. Without Claude Code or a connection the hook says what
will not run and asks whether to continue.

### Reproduce it

1. Install Claude Code and sign in (`claude`).
2. In the repository: `make setup` (sets `core.hooksPath` to `.githooks`; Docker must be running for `make verify`).
3. Commit and push as usual; the hooks do the rest.
4. Before handover: `claude -p "/quality-gate auto full <base>..HEAD" --permission-mode auto`, where `<base>` is the
   last commit of the original repository (here `c4ea358`). Inside an interactive `claude` session the same is
   `/quality-gate full <base>`.

`SKIP_AI_GATE=1 git push` skips only the AI review; `git push --no-verify` skips every check.

## Who did what

- **AI (Claude in the conversation):** analysis of the assignment, the plan and architecture, a step-by-step guide
  with proposed implementation for every step, discussed with the author before it was typed, the quality tooling (`Makefile`, git hooks, `CLAUDE.md`, agents,
  commands), all documentation, and triage of every review finding (fix, or record in `TRADEOFFS.md` with a reason).
- **Human (the author):** every decision (for example Ktor and Exposed instead of Spring, `POST` with `204`, days
  not incremented, two levels of AI review and the three-minute limit, which findings to fix); typing, reading,
  running and where needed changing every line of production and test code; reviewing the generated tooling and
  documents; atomic commits.
- **AI (Claude Code agents):** review only. They never edit code; their reports are in
  [`ai-reviews/`](ai-reviews/), and every accepted finding is traceable to a commit or a `TRADEOFFS.md` entry.

## How AI output was checked

AI suggestions were treated as claims to verify, not as answers.

- **Refactoring the calculator.** Characterization tests were written against the original code first. The
  refactored calculator was then compared with the original on 200,000 random inputs in the conversation, and by the
  `regression-hunt` agent on about 2.5 million; there were no differences. The full review before handover repeated
  the comparison against the original repository commit on 371,000 cases, again without a difference.
- **Mutation testing found weak tests.** `test-quality` showed that five mutants of the rounding and arithmetic
  (`BigDecimal.valueOf`, `HALF_EVEN`, `HALF_DOWN`, two arithmetic orders) survived the first characterization tests.
  The added rows were computed independently with exact decimal arithmetic before they were committed; all mutants
  are now caught.
- **Review caught what the first draft missed.** The first version of the balance update, as proposed in the
  implementation guide, read and wrote balances without row locks. `adversarial-code` showed that two overlapping
  updates could lose a month of interest. The fix (`SELECT … FOR UPDATE`) came with a test; `test-quality` then
  showed that the test would also pass without the fix, so it was rewritten to hold the row locks from a second
  connection. It was run with `.forUpdate()` removed (fails: 1000.83 instead of 2001.67) and restored (passes). The
  full review then showed, with a mutant, that splitting the read and the write into two transactions still passed;
  a second test that runs two updates at once now catches it.
- **The full review before handover** ran seven agents for about 20 minutes. It found no regression and no defect in
  the interest rules. Its findings led to one more test, the assumptions written as code comments (the assignment
  asks for that), and a round of corrections to these documents; the rest is recorded in
  [`TRADEOFFS.md`](TRADEOFFS.md) with its source.
- **Rejected findings.** Agent claims were checked before acting: "the Hikari pool is never closed per test" and
  "`.forUpdate()` has no import" were false (the logs show the pool closing; `forUpdate` is a member function and the
  build passed). They were not acted on.

## How the harness evolved

The setup was changed when it did not work in practice; each change is a commit.

- The first push gate reviewed everything with Opus and took over 15 minutes for a two-class change. A measured run
  used 8% CPU: the time was model latency, not builds. It became two levels (push: Sonnet, read-only, parallel,
  3-minute limit; handover: full), and a push review now takes 1–2 minutes.
- A review interrupted with Ctrl+C was recorded as complete. The gate now prints `QUALITY-GATE: DONE` only when every
  agent finished, and the hook records the review only after that line.
- Agent reports were long narratives; the output format was fixed to at most ten findings with four bullets each.
- The gate did not review changes to itself; `.githooks` and `.claude` are now in the hook paths.

## Limits

- Push-time findings are SUSPECTED: the agents only read the code there. A human or `/quality-gate full` confirms
  them.
- The agents and the conversation used models of the same family (Claude), so their independence comes from a fresh context and
  a hostile prompt, not from a different model.
- `/review-step` exists for interactive use; in this project the commit-level check was the `pre-commit` hook plus a
  review of each staged diff in the conversation.
