# Operational risk register and release readiness

**Review date:** 2026-10-17. **Current release state:** Deployment configuration is implemented; target-environment acceptance remains pending. This document records the evidence and operational work required before a production rollout.

This register complements [the hardening review](./HARDENING_REVIEW.md), [assessment readiness](./ASSESSMENT_READINESS.md), [security scanning](./SECURITY_SCANNING.md), and [deployment guidance](./DEPLOYMENT.md). Statuses describe evidence in this repository and saved local results; they do not imply that an external environment has been validated.

Status terms: **Implemented and tested**, **Partially verified**, **Not implemented**, **External validation pending**, or **Product decision required**.

## Operational risk register

| # | Area | Status | Current evidence and remaining work |
|---:|---|---|---|
| 1 | Duplicate or concurrent execution of one run | Partially verified | Pessimistic row lock and `@Version`; concurrency tests run on H2. PostgreSQL and multi-instance coordination have not been verified. |
| 2 | Duplicate approval decision | Implemented and tested | A second decision returns `409`; covered by integration tests, BDD, and API smoke. |
| 3 | Bounded retries | Implemented and tested | Three-attempt policy covered by engine tests and retry-exhaustion BDD. |
| 4 | Recovery of runs interrupted in `RUNNING` or `RETRYING` | Not implemented; product decision required | No startup reconciler or heartbeat exists. A crashed run may remain non-resumable until operator intervention. See D1. |
| 5 | Approval expiry | Not implemented; product decision required | Pending approvals do not expire automatically. See D2. |
| 6 | Idempotency keys for create/execute requests | Not implemented; product decision required | Repeated URL or run creation requests can create distinct resources. See D3. |
| 7 | Distributed ownership and leases | Not implemented | Execution coordination assumes a single service instance; database row locks are not a distributed lease/recovery design. |
| 8 | PostgreSQL and Flyway behavior | External validation pending | Four `PostgresPersistenceContractTest` cases were skipped because Docker was unavailable. The corresponding H2 contract tests ran; PostgreSQL behavior remains unverified. |
| 9 | OIDC discovery, JWKS, and production authorization | Partially verified; external validation pending | Synthetic JWT/MockMvc tests and a local RSA-key simulation cover route authorization, signatures, issuer/audience validation, and fail-closed configuration. No live issuer, JWKS rotation, or real claim mapping was tested. |
| 10 | TLS termination | Deployment responsibility | The application profile requires database TLS; public transport TLS and backend network isolation must be provided by the deployment gateway/platform. |
| 11 | Rate limiting and abuse controls | Not implemented in the application | Configure and test request, body-size, and abuse limits at a trusted gateway or add an application control. |
| 12 | Destination and egress protection | Partially verified | Literal private/local destinations are rejected. DNS resolution is intentionally not performed; DNS rebinding and public names resolving to private addresses require resolver/egress policy. Redirect targets are not revalidated at click time. |
| 13 | Audit integrity and retention | Partially verified | A per-run hash chain detects ordinary edits, reordering, and truncation. The anchor is stored in the same database and is not externally signed or immutable. |
| 14 | Dependency advisories | Partially verified | The 2026-10-17 OSV scan covered 98 runtime artifacts and reported two accepted critical `spring-webmvc:6.2.19` advisories, with zero unaccepted findings. The advisories remain present and are not resolved; rationale and review triggers are in [SECURITY_SCANNING.md](./SECURITY_SCANNING.md). |
| 15 | Working-tree and Git-history secret scanning | Partially verified | Local scan (2026-10-17 01:40): 236 candidate files, zero unreadable files, zero unallowed matches, and four exact synthetic fixtures. Git-history scanning has not run locally. |
| 16 | GitHub Actions | External validation pending | Workflow syntax passed the recorded local `actionlint` v1.7.12 check; no GitHub-hosted workflow run has occurred. |
| 17 | Load, soak, and failure testing | Partially verified | Local HTTP smoke results are recorded in [LOAD_TEST_PLAN.md](./LOAD_TEST_PLAN.md). No production-like soak, fault-injection, PostgreSQL, or multi-instance test has been performed. |
| 18 | Independent security assessment | Not performed | Schedule threat-model review and independent security testing against the release configuration and artifact. |

## Product decisions

These decisions affect recovery and externally visible behavior; they are intentionally left to the product and operations owners rather than inferred in code.

### D1 — Interrupted orchestration runs

Options:

1. Reconcile stale runs to `FAILED` or `SAFE_STOPPED`, append an audit event, and require an operator to resume or replan. This is the recommended default because it avoids silently repeating side effects.
2. Automatically resume. This risks repeating non-idempotent steps and previously approved work.
3. Provide operator-only recovery without a startup reconciler.

**Decision required:** select the recovery outcome and staleness threshold/heartbeat policy. Validate with PostgreSQL fault-injection tests before enabling automatic recovery.

### D2 — Approval expiry

Options:

1. Expire pending approvals to an explicit terminal or paused state and fail/compensate the run (recommended fail-closed behavior).
2. Keep approvals pending and alert operators.
3. Escalate to another approver.

**Decision required:** define time-to-live by risk class and the resulting run state. Add audit events and lifecycle tests for the selected behavior.

### D3 — Request idempotency

Recommended design: optional `Idempotency-Key` support for URL creation and run create/execute, with a unique persistence constraint and stored response.

**Decision required:** key scope, retention period, and behavior when a key is reused with a different body (recommended `409` or `422`). Verify persistence semantics on PostgreSQL.

The project maintainer owns implementation and evidence updates; the product owner/release approver owns D1–D3 decisions. Infrastructure and identity-provider validation must be owned by the deployment operator and identity-provider administrator.

## Deployment acceptance checklist

Before routing production traffic, record the target environment, named owners, reproducible evidence, and release approval for each applicable item:

1. **Database:** run Flyway against the target PostgreSQL version; verify pooling, timeouts, least-privilege credentials, database TLS, backups, restore, and failover.
2. **Identity:** use a real test issuer to verify discovery, JWKS retrieval/rotation, issuer, audience, scopes, expiry, outage behavior, and approval attribution.
3. **Network and edge:** terminate HTTPS at a trusted gateway, restrict direct service access, and configure rate, body-size, and abuse limits.
4. **Destination policy:** test DNS rebinding and private-address resolution; enforce resolver-aware allowlists or outbound network policy where required.
5. **Orchestration:** resolve D1–D3; define single-instance versus multi-instance operation; test restart recovery, duplicate delivery, approval lifecycle, and safe-stop persistence.
6. **Audit and operations:** define independent audit export/retention, access review, alerts, log redaction, incident response, rollback, on-call ownership, and recovery procedures.
7. **Security assurance:** review the accepted Spring advisories on each supported Spring Boot update and before release; run dependency and Git-history secret scans; complete threat-model and independent security review.
8. **Reliability:** perform production-like load, soak, and failure testing; define SLOs and alert thresholds. Readiness/liveness groups are not currently configured; validate health checks before relying on them in orchestration.
9. **Release evidence:** run CI on the target branch, retain test and scan reports, identify the artifact digest/configuration, and record reviewer approval and rollback plan.

The recorded local Maven, BDD, API, load, and scan results are summarized in [TEST_RESULTS.md](./TEST_RESULTS.md) and [EVIDENCE.md](./EVIDENCE.md). They are useful assessment evidence but do not substitute for the environment-specific checks above.
