# Questrade cash overlay

Android app (Kotlin) that shows how much cash the selected Questrade account holds in the order's currency, on the order screen. Scope: `docs/intent/questrade-cash-overlay.md`. Steps: `docs/plan.md`, one GitHub issue per step.

## Finishing work

1. Merge the finished branch into `main` and push `main`. Don't leave work on a branch or in an open PR.
2. Once `git branch -r --contains <branch>` lists `origin/main`, remove the worktree with `git worktree remove` and delete the branch.

## Agent skills

### Issue tracker

Issues live in this repo's GitHub Issues, managed with `gh`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Failure log

One imperative line per mistake that happened at least once, inserted at a random spot in the list.
