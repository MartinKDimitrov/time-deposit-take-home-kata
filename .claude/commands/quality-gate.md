---
description: Review agents chosen by what changed. Runs automatically from the pre-push hook (push budget); `full` runs every agent before handover
argument-hint: "[auto] [full] [<base>..<tip> | <base-ref>]"
---

Run the AI quality gate on a commit range. Arguments: $ARGUMENTS

1. **Mode and range.**
   - The mode is `full` when `full` is in the arguments, otherwise `push`. `push` is the quick gate that runs on
     every `git push` and must finish within three minutes; `full` is the thorough review before handover or release.
     In `push` mode keep every message short: no tables, no restating of findings beyond one line each.
   - An explicit range `<base>..<tip>` in the arguments wins (the pre-push hook always passes one).
   - Otherwise the commits not yet pushed, `@{upstream}..HEAD`; without an upstream,
     `$(git merge-base HEAD origin/HEAD)..HEAD`.
   - `full` without an explicit range: everything since the base ref given in the arguments, otherwise since
     `git merge-base HEAD origin/HEAD`.

   Print the mode, the range and `git diff --stat` for it. If the range is empty, say so and go to step 7.

2. **Classify by what changed, not by how much.** A one-character change to a business rule can matter more than
   a 500-line rename. Use `git diff --name-only`, the diff itself and `git log --format=%s` for the range.
   A range can have several classes.
   - **spec** – requirement, plan or decision documents changed
   - **refactor** – commits typed `refactor:`, or production code changed while test expectations did not
   - **logic** – production code that decides an outcome changed: business rules, calculations, conditions,
     boundaries, rounding, persistence or mapping of data
   - **structure** – packages or modules added or moved, build-file or dependency changes, a change that crosses layers
   - **tests** – tests added or changed
   - **docs** – README or `docs/**` changed (except `docs/ai-reviews/`)
   - **fixes** – commits that address findings recorded in `docs/ai-reviews/`

3. **Select agents.** Only agents installed in `.claude/agents/` or `~/.claude/agents/`; each runs at most once.

   | Class | `push` | `full` |
   | --- | --- | --- |
   | spec | – | spec-vs-plan |
   | refactor | test-quality | regression-hunt, test-quality |
   | logic | adversarial-code, test-quality | adversarial-code, test-quality |
   | structure | – | adversarial-architecture |
   | tests | test-quality | test-quality |
   | docs | – | docs-claims-audit |
   | fixes | verify-findings-closed | verify-findings-closed |

   In `push` mode list the classes whose agents were deferred, so the human knows `full` still has to run them.
   If no agent is selected (for example only IDE, ignore or formatter configuration changed), say so and go to step 7.
   Cost follows scope, not a size threshold: pass every agent only the files of its class.

4. **Plan.** Show each class found with the files that triggered it, the agents chosen and a one-line reason
   (in `push` mode: one line per agent). With `auto` in the arguments (the pre-push hook) continue immediately and
   never ask questions; otherwise wait for confirmation.

5. **Run.** Start all selected agents at once, in a single message, in the foreground (never in the background),
   and wait until every one of them has finished. Each agent builds only in its own worktree, so they do not wait
   for each other. Tell each agent the mode (`Mode: push` or `Mode: full`), the exact range and its files.
   In `push` mode start every agent with the `sonnet` model, overriding its own; in `full` mode use the agent's own model.

6. **Summarize** in at most 15 lines (`push` mode: at most 6): per agent the verdict and its top findings, with a link to
   `docs/ai-reviews/<agent-name>.md`. Do not fix anything.

7. **Close.** Remind the human to commit the reports (`docs: add AI review reports`) before pushing, and to record
   any finding that is accepted rather than fixed, with the reason, in the project's trade-offs document.
   Then print, as the very last line, exactly `QUALITY-GATE: DONE`. Print it only when every selected agent
   finished and wrote its report; if an agent failed or was stopped, name it and do not print that line.
