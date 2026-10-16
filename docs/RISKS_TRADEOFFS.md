# Risks, trade-offs, assumptions, and limitations

This document summarizes design trade-offs and technical boundaries. Dated integration evidence and release responsibilities are maintained in [OPERATIONS_AND_RELEASE_READINESS.md](./OPERATIONS_AND_RELEASE_READINESS.md), with environment setup in [DEPLOYMENT.md](./DEPLOYMENT.md).

## Risks

- **Production auth is configured, not deployment-verified.** The production profile validates bearer JWT signatures/issuer using configured OIDC discovery, checks a required audience, and requires the `agentic.operator` scope. MockMvc test tokens exercise route authorization; no live identity provider, JWK rotation, issuer outage, or production token exchange was tested. TLS remains a trusted-gateway responsibility.
- **Local profile is unauthenticated.** The default profile binds to loopback; do not override that binding or expose it on a shared/public network.
- **Rate limiting is external.** No app-level rate limiter is included; production must enforce create/redirect/API limits at a trusted gateway.
- **Destination policy is DNS-independent.** Literal private/local addresses and common internal suffixes are rejected without DNS resolution. Public DNS names can still resolve to private addresses or change over time; enforce resolver-aware policy at the gateway if that threat is in scope.
- **Persistence differs by profile.** Local/test H2 state is ephemeral and lost on restart. Production configuration targets PostgreSQL with Flyway migrations, schema validation and `verify-full` database TLS by default, but no live database was available; backups, restore, failover, pooling, capacity and recovery remain unverified. A process restart can still leave orchestration runs without automatic recovery.
- **Generated artifacts are proposals, not applied changes.** This is intentional to prevent model output from modifying executable application source. A human-controlled integration/deployment process would be needed to apply a proposal.
- **Mock output is not evidence of model quality.** The deterministic provider makes runs repeatable and credential-free, but it does not assess a real LLM's correctness, prompt-injection resilience, or reliability.
- **Audit hashes share the event database.** The per-run hash chain and run tip anchor detect ordinary event edits, reordering, and truncation. They are not signed or externally anchored, so an attacker with full database write access could rewrite both the chain and its anchor.

## Trade-offs

- **Single-module application:** easier to run, test, and review for an assessment, at the cost of deployment and scaling boundaries.
- **H2 in memory:** minimizes setup and makes the sample self-contained, at the cost of durability and multi-instance coordination.
- **Database execution claim:** a pessimistic lock prevents duplicate run execution when callers share the database; durable multi-instance leases/recovery and operational database hardening remain out of scope.
- **Fixed DAG and scenario checks:** guarantees reproducible, allow-listed execution and avoids arbitrary command invocation, but new checks require a reviewed Java code change.
- **Cooperative safe-stop:** gives tasks a bounded opportunity to stop and compensate safely, but cannot immediately terminate work that ignores its stop token or blocks indefinitely.
- **Sequential and bounded parallel modes:** exercise dependency synchronization while keeping scheduling understandable; cross-process scheduling and distributed leases are not implemented.
- **Default 30-day expiry:** makes the shortener enhancement concrete and predictable; the value is configurable and explicit per-link expiry takes precedence.

## Assumptions

- The assessment demonstration is run with JDK 21, Maven, and an in-memory H2 database. The default profile is bound to loopback; production-profile access is used only for the security integration tests unless separately configured.
- Human approvals are performed through the REST API while that process remains running.
- The mock LLM output is sufficient to demonstrate agent routing, task context, decision lineage, and sandboxed artifact handling.
- Brownfield validation targets the shortener's Java service and persistence behavior; it does not build or run generated source.

## Limitations / out of scope

- No production LLM provider or model scoring/evaluation.
- PostgreSQL and OIDC production profile configuration are implemented, but not verified against live services; no provider-specific claim mapping or identity lifecycle is included.
- No TLS termination or in-process rate limiter; the chosen deployment expects trusted gateway controls.
- No arbitrary shell, build tool, or model-generated command execution.
- No direct application of agent-generated patches and no production deployment.
- Metrics aggregate persisted terminal runs, retry decisions, recovery events, and decided approvals at request time; this is not intended as a high-volume monitoring backend.
- No multi-tenant isolation or distributed orchestration.
- PostgreSQL backup/restore/failover, orchestration crash recovery, disaster-recovery runbooks, penetration testing, and production-scale load testing require environment-specific implementation and evidence; track these in the operational release checklist.
