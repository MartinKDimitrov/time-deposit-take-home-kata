---
description: Before git push - run the review agents that the not-yet-pushed commits actually need
argument-hint: "[full] [base-ref]"
---

Run the AI quality gate on a commit range. Arguments: $ARGUMENTS

1. **Range.**
   - Default, before a push: the commits not yet pushed, `@{upstream}..HEAD`. Without an upstream, use
     `$(git merge-base HEAD origin/HEAD)..HEAD`.
   - `full`, before handover or release: everything since the base ref given in the arguments, otherwise since
     `git merge-base HEAD origin/HEAD`.

   Print the range and `git diff --stat` for it. If the range is empty, say so and stop.

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

   | Class | Agents |
   | --- | --- |
   | spec | spec-vs-plan |
   | refactor | regression-hunt, test-quality |
   | logic | adversarial-code, test-quality |
   | structure | adversarial-architecture |
   | tests (without logic) | test-quality |
   | docs | docs-claims-audit |
   | fixes | verify-findings-closed |

   If no class matches (for example only IDE, ignore or formatter configuration changed), run nothing and say so.
   Cost follows scope, not a size threshold: pass every agent only the files of its class, so a small change gets
   a small review.

4. **Show the plan** – each class found with the files that triggered it, the agents chosen and a one-line
   reason – and wait for confirmation.

5. **Run.** Agents that only read may run in parallel; agents that build or run tests run one after another.
   Pass each agent the exact range and its files.

6. **Summarize** in at most 15 lines: per agent the verdict and its top findings, with a link to
   `docs/ai-reviews/<agent-name>.md`. Do not fix anything.

7. **Close.** Remind the human to commit the reports (`docs: add AI review reports`) before pushing, and to record
   any finding that is accepted rather than fixed, with the reason, in the project's trade-offs document.
