# Risks, trade-offs, assumptions, and extension points

This document summarizes design trade-offs and technical boundaries. Dated integration evidence and release responsibilities are maintained in [OPERATIONS_AND_RELEASE_READINESS.md](./OPERATIONS_AND_RELEASE_READINESS.md), with environment setup in [DEPLOYMENT.md](./DEPLOYMENT.md).

## Risks

- **Production authentication configuration.** The production profile validates bearer JWT signatures/issuer using configured OIDC discovery, checks a required audience, and requires the `agentic.operator` scope. MockMvc test tokens exercise route authorization; live identity-provider, JWK rotation, issuer-outage, and production token-exchange evidence is tracked in the operational release register. TLS remains a trusted-gateway responsibility.
- **Local profile is unauthenticated.** The default profile binds to loopback; do not override that binding or expose it on a shared/public network.
- **Gateway abuse controls.** Configure create/redirect/API rate limits at a trusted gateway; the application does not include an in-process rate limiter.
- **Destination policy is DNS-independent.** Literal private/local addresses and common internal suffixes are rejected without DNS resolution. Public DNS names can still resolve to private addresses or change over time; enforce resolver-aware policy at the gateway if that threat is in scope.
- **Persistence differs by profile.** Local/test H2 state is ephemeral and lost on restart. Production configuration targets PostgreSQL with Flyway migrations, schema validation and `verify-full` database TLS by default. Target-database behavior, backups, restore, failover, pooling, capacity, and recovery are tracked as environment-specific validation; a process restart can leave orchestration runs requiring operator recovery.
- **Generated artifacts are proposals, not applied changes.** This is intentional to prevent model output from modifying executable application source. A human-controlled integration/deployment process would be needed to apply a proposal.
- **Deterministic mock scope.** The provider makes runs repeatable and credential-free; evaluating a real LLM's correctness, prompt-injection resilience, and reliability is separate provider-integration work.
- **Audit hashes share the event database.** The per-run hash chain and run tip anchor detect ordinary event edits, reordering, and truncation. They are not signed or externally anchored, so an attacker with full database write access could rewrite both the chain and its anchor.

## Trade-offs

- **Single-module application:** easier to run, test, and review for an assessment, at the cost of deployment and scaling boundaries.
- **H2 in memory:** minimizes setup and makes the sample self-contained, at the cost of durability and multi-instance coordination.
- **Database execution coordination:** a pessimistic lock prevents duplicate run execution when callers share the database; durable multi-instance leases/recovery and operational database hardening are extension work.
- **Fixed DAG and scenario checks:** guarantees reproducible, allow-listed execution and avoids arbitrary command invocation, but new checks require a reviewed Java code change.
- **Cooperative safe-stop:** gives tasks a bounded opportunity to stop and compensate safely, but cannot immediately terminate work that ignores its stop token or blocks indefinitely.
- **Sequential and bounded parallel modes:** exercise dependency synchronization while keeping scheduling understandable; cross-process scheduling and distributed leases are future capabilities.
- **Default 30-day expiry:** makes the shortener enhancement concrete and predictable; the value is configurable and explicit per-link expiry takes precedence.

## Assumptions

- The assessment demonstration is run with JDK 21, Maven, and an in-memory H2 database. The default profile is bound to loopback; production-profile access is used only for the security integration tests unless separately configured.
- Human approvals are performed through the REST API while that process remains running.
- The mock LLM output is sufficient to demonstrate agent routing, task context, decision lineage, and sandboxed artifact handling.
- Brownfield validation targets the shortener's Java service and persistence behavior; it does not build or run generated source.

## Current scope and extension points

- The provider boundary ships with a deterministic mock. Live-provider quality and prompt-resilience evaluation are separate extension work.
- The production profile configures PostgreSQL and OIDC; live integration, provider-specific claim mapping, and identity lifecycle validation are tracked in the operational release register.
- TLS termination and request-rate limits are platform controls in this design; configure them at a trusted gateway.
- Execution is limited to registered Java checks. Arbitrary shell/build-tool commands and model-generated command execution are deliberately excluded.
- Agent-generated patches remain proposals for human review; deployment and release execution are owned by the surrounding delivery platform.
- Metrics aggregate persisted terminal runs, retry decisions, recovery events, and decided approvals at request time; high-volume monitoring belongs in a dedicated telemetry backend.
- Multi-tenant isolation and distributed orchestration are extension points beyond the current single-instance assessment scope.
- PostgreSQL backup/restore/failover, orchestration crash recovery, disaster-recovery runbooks, penetration testing, and production-scale load testing require environment-specific implementation and evidence; track these in the operational release checklist.
