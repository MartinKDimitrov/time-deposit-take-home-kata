---
description: Review the uncommitted changes against the project rules before committing
---

Review the current uncommitted changes (`git diff` and `git diff --staged`, plus untracked files from `git status`)
against the rules in CLAUDE.md. Report findings only; do not edit files.

Check, in this order:

1. **Hard constraints** – `TimeDeposit` unchanged; `updateBalance` signature and behaviour unchanged;
   still exactly two endpoints; contract changed before code.
2. **Architecture** – dependencies point inwards; no Ktor/Exposed/DTO types in `domain/` or `application/`; entities and DTOs
   stay inside their adapter; money conversion only through `toMoney()`.
3. **Tests** – new behaviour has a meaningful test; boundaries covered; no redundant tests; no unnecessary mocks.
4. **Clean code** – naming, duplication, magic numbers, unused code or imports, comments that explain why.
5. **Commit scope** – the change is one logical step; suggest a Conventional Commit message for it.

Output a short list grouped as **Must fix**, **Should fix**, **Optional**, each item with `file:line` and a
one-line reason, then the suggested commit message. If there is nothing to fix, say so in one line.
