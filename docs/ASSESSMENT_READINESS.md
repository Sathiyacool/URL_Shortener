# Assessment readiness

## Overall status

**Assessment demo: ready for reviewer walkthrough. Production deployment: NO-GO — not ready / not certified.**

The project demonstrates the requested Java/Spring URL shortener and agentic orchestration behavior. It uses deterministic mock LLM output, persisted run/task/audit records, human approval gates, REST and CLI entry points, BDD/API/security tests, and three reproducible scenarios. Readiness does not imply that this sample is safe or operationally complete for a public production deployment.

The production profile is a **hardening baseline**, not a production launch artifact: PostgreSQL/Flyway and OIDC/JWT configuration exists, but current tests use H2 and synthetic JWTs. No production environment has been deployed or accepted.

## Readiness matrix

| Area | Assessment status | Evidence / remaining boundary |
|---|---|---|
| URL create, redirect, expiration, analytics | Ready | Unit, MockMvc, and BDD coverage; H2 is in memory |
| Dependency DAG and persisted execution | Ready | DAG, state machine, scheduler, concurrency, and persistence integration tests |
| Sequential and parallel scheduling | Ready | Deterministic scheduler integration tests |
| Approval gates and change control | Ready | High-impact task blocks, production-auth integration test verifies authenticated attribution |
| Retry/fallback/rollback/safe-stop | Ready | Execution engine integration coverage; safe-stop remains cooperative |
| Security and compliance controls | Partial | Local loopback default; production profile now validates OIDC issuer/audience JWTs and requires `agentic.operator` scope. MockMvc covers 401/403 and fail-closed config. Live IdP/JWK behavior, TLS gateway, rate limiting, resolver-aware destination rules, external audit anchoring remain unverified/deployment work |
| Production database configuration | Partial | Production config targets PostgreSQL, uses Flyway migrations, and Hibernate schema validation. Missing DB settings fail closed (real startup exit 1; see `evidence/prod-00-fail-closed-database.txt`); migration ran against H2 in tests, not live PostgreSQL. Backup/restore, failover and recovery are unverified |
| Reliability metrics and audit history | Ready for demonstration | Aggregate and tamper/truncation tests; chain anchor is stored in the same database |
| Dynamic replanning | Ready | Replan integration coverage |
| Greenfield/brownfield/ambiguous scenarios | Ready | API integration tests, BDD, and actual CLI runs recorded in `EVIDENCE.md` |
| Approval lifecycle (pending, approve, reject, resume, safe-stop) | Ready | `ScenarioLifecycleIntegrationTest` plus eight real CLI transcripts in `evidence/` (REQ-08, 11, 17) |
| API error and authorization behavior | Ready for demo | `ApiErrorContractTest`, current OIDC `ProductionAuthorizationBoundaryTest` and `ProductionFailClosedTest`; historical 19 live HTTP checks used the superseded Basic-auth profile. `403` is MockMvc-only |
| Test suite | Verified | Final `mvn clean verify` 2026-10-16: **111 tests, 0 failures, 0 errors, 1 Windows symlink setup skip**; BDD 6 scenarios / 22 steps passed; details in `TEST_RESULTS.md` |
| Screenshot evidence | **Not captured** | Text transcripts exist; Windows capture steps are in `EVIDENCE.md` (REQ-23) |
| Memory Bank | Ready | `memory-bank/` cross-referenced with these documents (REQ-22) |
| Repository publication | Requested | Publishing this assessment project does not imply production release approval or change the NO-GO decision above |

## Production go-live gates

**Decision: do not expose this application to production traffic until every applicable gate below has been completed and evidenced.** A configuration change or passing local test is not by itself production acceptance.

| Gate | Required evidence before go-live | Current status |
|---|---|---|
| PostgreSQL | Run migrations on the target PostgreSQL version; verify schema validation, connection pooling/timeouts, least-privilege DB account, encryption at rest, backups, restore drill, and failover/recovery | Not verified against a live database |
| OIDC | Validate issuer discovery/JWK signature checks, issuer/audience and required scope with real issuer-issued tokens; test expiry, invalid signature, key rotation, issuer outage and approval attribution | MockMvc/unit only; live issuer not tested |
| Transport and edge security | Deploy a trusted TLS gateway; restrict backend network access; enforce rate and body-size limits on public creation/redirect endpoints and protected APIs | Not configured or tested here |
| Destination/egress policy | Test DNS rebinding and hostnames resolving to private/link-local destinations; enforce resolver-aware allowlist or outbound egress restrictions | DNS resolution intentionally not performed; deployment policy absent |
| Orchestration recovery | Define and test crash recovery for RUNNING/RETRYING tasks, idempotency, abandoned approvals, safe-stop after process restart, and single-instance ownership; define whether multi-instance operation is prohibited | Crash recovery and leases not implemented |
| Audit and retention | Export/anchor audit data to an independently controlled immutable store; establish retention, access review, and incident investigation procedure | Hash-chain anchor remains in the same database |
| Security assurance | Run dependency/CVE scanning, secret scanning, threat model, penetration test, and remediation review against the exact release artifact | Not run |
| Reliability and operations | Run production-like load/soak/failure tests; configure alerts, dashboards, log retention/redaction, health/readiness probes, SLOs, on-call ownership, rollback and disaster-recovery runbooks | Not evidenced |
| Release evidence | Re-run full CI and BDD; capture genuine release evidence; record reviewer approval, artifact digest, deployment configuration and rollback plan | CI passed locally; screenshots and deployment evidence missing |

### Promotion criteria

Change the decision from **NO-GO** only when the gates applicable to the target deployment have named owners, reproducible evidence, and explicit sign-off. Reassess this table after the live PostgreSQL/OIDC integration run and after each security or deployment architecture change. Until then, use the default profile only for local assessment demonstrations and keep the production profile in controlled test environments.

## Risks to disclose in a walkthrough

- The default profile is unauthenticated by design and binds only to `127.0.0.1`; do not expose it on a shared network.
- The production profile is configured for PostgreSQL and OIDC/JWT, but those integrations were not tested against live services. The old `rest-*.txt` HTTP Basic transcripts are historical and do not verify the new profile.
- OIDC provider setup must issue tokens with the configured audience and `agentic.operator` scope. Use a secret manager for database credentials. TLS must terminate at a trusted gateway.
- Rate limiting and DNS resolver/egress controls are not implemented in-process.
- Default-profile H2 data is ephemeral; restarting loses runs, approvals, click history, and audit records. Production PostgreSQL backup, restore and failover are not verified.
- The mock LLM demonstrates provider isolation and repeatability, not the quality or safety of a real model.
- Agent code is not applied to application source. Checks are fixed Java implementations; generated model commands are never executed.
- Audit hashes are tamper-evident only relative to the stored database anchor, not independently signed or externally anchored.
- The Windows symlink escape assertion requires OS permission to create a symlink. The test skips only if link setup itself is denied; the test plan documents how to enable and rerun it.
- No dependency vulnerability scanner was configured or run; Maven `verify` is not a CVE scan.
- No production load, penetration, disaster-recovery, or live OIDC/JWK rotation test has been completed. Production readiness is not claimed.

## Reviewer walkthrough

1. Run `mvn clean verify`; compare the output with `TEST_RESULTS.md`.
2. Follow the URL API walkthrough in the README and demonstrate expiry/analytics.
3. Run the headless CLI commands in `SCENARIOS.md`, including the `--decision` lifecycle options; compare outcomes with `EVIDENCE.md`.
4. Use web mode for the REST approval-and-resume path in `SCENARIOS.md`.
5. Review `TRACEABILITY.md` to map assessment criteria to code and tests.
6. Review `RISKS_TRADEOFFS.md` and disclose limitations; do not characterize the sample as production-ready.
