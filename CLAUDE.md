# SeatSense

Library seat finder for our Enterprising Web Applications (Java) course project. Two people build it: **Shefali** and **Bhavyam**, each with their own Claude Code session, collaborating through this GitHub repo.

## Read first
1. This file.
2. `docs/PROJECT.md`: the full idea, architecture, data model, API and algorithms.
3. `docs/WORKPLAN.md`: who does what, phase by phase. Ask which person you are helping if it is not clear, then work only on that person's tasks.
4. `docs/api.md` once it exists: the API contract.

## Before any work: sync with GitHub
Both people work on this repo at the same time, so the local copy and GitHub must match before anything else happens.

1. `git fetch origin`
2. `git status`. If there are uncommitted local changes, do not overwrite or discard them. Tell the person and ask what to do.
3. If on `main`: `git pull --ff-only origin main`.
   If on a feature branch: `git merge origin/main` into it (resolve conflicts with the person, never by deleting their work).
4. If `CLAUDE.md` or anything in `docs/` changed in what you pulled, re-read it before continuing. Rules and assignments may have changed.
5. Check `docs/WORKPLAN.md` for which tasks are now ticked, so you do not redo or block on finished work.

**When finishing work:** commit, push the branch, open a pull request, and tick the task in `docs/WORKPLAN.md` in that same PR. After a PR is merged, switch to `main` and pull again.

Never force-push to `main`. Never rewrite history that has been pushed.

## Rules
- Work only in modules owned by the person you are helping (`docs/WORKPLAN.md`, Ownership). If a change is needed in the other person's module or in a shared file, stop and say so.
- Never change an existing Flyway migration. Add a new one. Shefali uses odd versions (V1, V3, ...), Bhavyam uses even (V2, V4, ...).
- Java 21 (`release 21`), Spring Boot 3.5.x, Maven wrapper `./mvnw`. Do not raise the Java version.
- Every new backend behaviour comes with a test.
- All time goes through the injectable clock (`common/TimeProvider`).
- Commit messages: short, imperative, written as the person committing. No AI attribution, no "Co-Authored-By" lines, no "Generated with" lines in commits, PRs, issues or the README.
- Never commit secrets (.env, passwords, keys). Use .env.example.
- Follow the design rules in docs/PROJECT.md Section 17 for any UI or copy.
- Sample data must be labelled as sample data. Never invent statistics.
