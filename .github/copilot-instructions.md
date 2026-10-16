# Copilot Agent instructions

## Required startup

1. Read `memory-bank/README.md`.
2. Read `memory-bank/projectbrief.md`, `activeContext.md`, `progress.md`, and `techContext.md`.
3. Read `systemPatterns.md`, `decisionLog.md`, and `testStrategy.md` when relevant.
4. Inspect the actual source, current `git status`/diff, and recent history. Treat repository code and fresh command output as authoritative over handover notes.
5. Identify the last verified Maven and BDD results, including failures, skips, and work not executed.

## Project invariants

- Keep application and orchestration logic in Java 21 / Spring Boot 3.x under `com.schwab.agentic`.
- Preserve the deterministic `LlmProvider` mock so local runs need no API key.
- Never execute shell commands, code, or arbitrary checks generated or selected by an LLM. Use only fixed registered Java operations.
- Keep high-impact work behind explicit human approval. Preserve safe-stop, bounded retry, fallback/compensation, audit, and change-control behavior.
- Default/local configuration is loopback-only and unauthenticated. The `prod` profile requires configured OIDC/JWT and PostgreSQL; do not describe it as production-ready without live integration and operational evidence.
- Production API docs remain disabled and denied. Do not expose credentials, tokens, personal data, or production data in code, docs, logs, or screenshots.
- Keep status terms consistent: Implemented, Tested, Partial, and Not implemented / Not evidenced. A skipped assertion is not a passing test.

## Change and verification workflow

1. Confirm scope and inspect existing patterns before editing. Preserve unrelated work and avoid broad rewrites.
2. Prefer focused tests while iterating. Before completion, run the relevant tests; run `mvn clean verify` for a final verification when practical.
3. Report the exact commands and observed counts. Do not reuse stale results or claim tests, screenshots, deployments, or external integrations that were not executed.
4. Update `memory-bank/activeContext.md` and `progress.md` after meaningful work. Update `systemPatterns.md`, `techContext.md`, and `decisionLog.md` for architectural, dependency, security, or workflow changes.
5. Reconcile `README.md` and applicable canonical `docs/` files, especially `PLAN.md`, `TRACEABILITY.md`, `TEST_PLAN.md`, `TEST_RESULTS.md`, `ASSESSMENT_READINESS.md`, and `EVIDENCE.md`.
6. Keep production readiness evidence-based; report outstanding deployment gates and missing screenshots explicitly.
7. Do not commit or push unless the user explicitly authorizes that action for the current work. Never force-push.
