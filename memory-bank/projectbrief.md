# Project brief

## Objective (verified from the user's assessment brief)

Charles Schwab AI Engineering Assessment: a runnable, Java-only **Agentic Software Engineering System** around a **URL shortener**. The system plans and runs engineering work as a persisted dependency DAG, with human approvals and audit-grade observability, without needing an LLM API key.

Current status (2026-10-16): assessment implementation verified; production profile now has PostgreSQL/Flyway and OIDC/JWT configuration, but production readiness is **not claimed** because live infrastructure, operational safeguards, and deployment validation remain incomplete.

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

Constraints: LLM behind an interface with a deterministic mock; never execute AI-generated commands; risky operations require explicit approval; do not claim tests pass unless executed.

## Deliverables

Source, tests, `README.md`, `docs/` (plan, architecture, scenarios, risks, test plan, traceability, results, readiness, evidence), `evidence/` transcripts, this `memory-bank/`.

## Acceptance criteria

The 23 requirement IDs and statuses live in [docs/TRACEABILITY.md](../docs/TRACEABILITY.md). Acceptance is evidenced by `mvn clean verify` plus the CLI/REST transcripts in `evidence/`. Production readiness is explicitly **not** an acceptance claim.

## Confirmed user decisions

JDK 21 and Maven; Spring Boot 3.x; base package `com.schwab.agentic`; brownfield scenario enhances the shortener's own code (default URL expiration); REST is the primary interface plus a CLI reusing the same services. An initial commit and push to the user-specified repository has been requested; inspect Git status to confirm the actual publication state.
