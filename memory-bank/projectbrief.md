# Project brief

## Objective (verified from the user's assessment brief)

Charles Schwab AI Engineering Assessment: a runnable, Java-only **Agentic Software Engineering System** around a **URL shortener**. The system plans and runs engineering work as a persisted dependency DAG, with human approvals and audit-grade observability, without needing an LLM API key.

Current status (2026-10-17): assessment implementation verified; the production profile includes PostgreSQL/Flyway and OIDC/JWT configuration. Environment-specific integration and operational validation are recorded in `docs/OPERATIONS_AND_RELEASE_READINESS.md`.

## Business problem

Show that AI-assisted software delivery can be controlled: explicit task dependencies, bounded failure handling, human sign-off for risky changes, and a defensible audit trail.

## Scope (all in Java 21 / Spring Boot 3.x / Maven / Spring Data JPA / H2 / JUnit 5 / Mockito / Cucumber)

1. URL shortener APIs: create, redirect, expiration, click analytics.
2. Orchestration engine: DAG, task states, execution history, decision lineage.
3. Sequential and parallel execution with synchronization.
4. Human approval checkpoints for high-impact work.
5. Bounded retries, fallback, rollback/compensation, safe-stop.
6. Security, compliance and change-control guardrails.
7. Metrics: success rate, retries, rollback frequency, MTTR, end-to-end latency.
8. Dynamic replanning.
9. Three scenarios: greenfield, brownfield, ambiguous.
10. Tests, architecture docs, setup, risks, trade-offs, assumptions, limitations.
11. Generated OpenAPI contract with interactive documentation limited to the local profile.
12. Reusable Copilot Agent instructions that enforce repository startup, verification, and handover workflows.

Constraints: LLM behind an interface with a deterministic mock; never execute AI-generated commands; risky operations require explicit approval; do not claim tests pass unless executed.

## Deliverables

Source, tests, `README.md`, `.github/copilot-instructions.md`, `docs/` (plan, architecture, scenarios, risks, test plan, traceability, results, readiness, evidence), `evidence/` transcripts, this `memory-bank/`.

## Acceptance criteria

The 25 requirement IDs and statuses live in [docs/TRACEABILITY.md](../docs/TRACEABILITY.md). Assessment behavior is evidenced by `mvn clean verify` plus the CLI/REST transcripts in `evidence/`; deployment acceptance has separate environment-specific criteria.

## Confirmed user decisions

JDK 21 and Maven; Spring Boot 3.x; base package `com.schwab.agentic`; brownfield scenario enhances the shortener's own code (default URL expiration); REST is the primary interface plus a CLI reusing the same services. The user-specified repository is `https://github.com/Sathiyacool/URL_Shortener`; commit `cbbbbd2` was pushed to `main`. Always inspect current Git state rather than assuming it remains unchanged.
