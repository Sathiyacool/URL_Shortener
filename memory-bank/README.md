# Memory Bank

A repository-based handover so a new Copilot Agent session can continue consistently. It is a **navigation aid**, not a substitute for source code, executable tests or current verification. Facts are dated; when a fact conflicts with the source or a fresh test run, the source and the run win and the Memory Bank must be corrected.

Last updated: **2026-10-16**. Check `activeContext.md` for the most recent verified Maven and BDD results; do not assume counts in this overview remain current. The project is not certified or production-ready; see `activeContext.md` and `docs/ASSESSMENT_READINESS.md`.

## Files

| File | Purpose |
|---|---|
| [projectbrief.md](./projectbrief.md) | Assessment objective, scope, deliverables, acceptance criteria |
| [productContext.md](./productContext.md) | URL shortener use cases, the three scenarios, expected behavior |
| [systemPatterns.md](./systemPatterns.md) | Architecture, components, DAG orchestration, persistence, REST, security, approval gates |
| [techContext.md](./techContext.md) | Versions, dependencies, configuration, Windows notes, exact build/test commands |
| [activeContext.md](./activeContext.md) | Current status, last verified results, open issues, next steps |
| [progress.md](./progress.md) | Phases, verified features by requirement ID, outstanding work, limitations |
| [decisionLog.md](./decisionLog.md) | Significant decisions, alternatives, consequences |
| [testStrategy.md](./testStrategy.md) | Test pyramid, responsibilities, data, security and evidence rules |

Canonical documents (link, do not duplicate): [README](../README.md), [PLAN](../docs/PLAN.md), [ARCHITECTURE](../docs/ARCHITECTURE.md), [SCENARIOS](../docs/SCENARIOS.md), [TEST_PLAN](../docs/TEST_PLAN.md), [TRACEABILITY](../docs/TRACEABILITY.md) (defines `REQ-nn` IDs), [TEST_RESULTS](../docs/TEST_RESULTS.md), [ASSESSMENT_READINESS](../docs/ASSESSMENT_READINESS.md), [EVIDENCE](../docs/EVIDENCE.md), [RISKS_TRADEOFFS](../docs/RISKS_TRADEOFFS.md).

Repository-wide Copilot guidance is in [`.github/copilot-instructions.md`](../.github/copilot-instructions.md); follow it together with this Memory Bank.

## Startup workflow (required before changing anything)

1. Read this README.
2. Read `projectbrief.md`, `activeContext.md`, `progress.md`, `techContext.md`.
3. Read `systemPatterns.md`, `decisionLog.md`, `testStrategy.md` when relevant to the task.
4. Inspect the actual source and `git status` / `git diff`, and check `git log` and remotes before proceeding. Never assume the worktree or branch state from this handover.
5. Identify the last verified test results (`activeContext.md`) and any uncommitted changes.

## Completion workflow (required after changing anything)

1. Run the relevant tests, then `mvn clean verify` for a final result (commands in `techContext.md`).
2. Update `activeContext.md` and `progress.md` with actual results, including skipped and unexecuted tests.
3. Update `systemPatterns.md`, `techContext.md` and `decisionLog.md` when architecture, dependencies, security or workflow decisions change.
4. Keep `docs/TEST_RESULTS.md`, `docs/EVIDENCE.md`, `docs/ASSESSMENT_READINESS.md`, `docs/PLAN.md` and `README.md` consistent with the same numbers.
5. Summarize what changed, what remains incomplete, and the next recommended action.

## Rules

- Never mark a feature complete or a test passed without implementation or test-execution evidence.
- Distinguish **verified**, **planned**, **assumed** and **unresolved** items.
- Never store credentials, tokens or secrets here. Throwaway credentials used in evidence runs are set only in a process environment.
