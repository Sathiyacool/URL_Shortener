# Plan: Agentic Software Engineering System for a URL Shortener

Status: P0–P14 assessment, production-profile hardening, OpenAPI, Copilot handover, final verification, and publication are complete. Commit `cbbbbd2` was pushed normally to `https://github.com/Sathiyacool/URL_Shortener` on `main`; the branch was clean and tracking `origin/main` after push. Final `mvn clean verify`: 114 tests (113 passed, 0 failures, 0 errors, 1 Windows setup skip); explicit BDD: 6 scenarios / 22 steps, no failures or skips. This does **not** establish production readiness; live PostgreSQL/OIDC verification and operational controls remain outstanding.

## Reassessment and prioritized extension plan

The following is a code-and-documentation review against the supplied assessment dimensions. Existing implementation is not treated as evidence unless the source, executable tests, or recorded run support it.

| Priority | Dimension | Current evidence and remaining work | Planned action / exit evidence |
|---|---|---|---|
| P1 | TECH — API and software design | Java/Spring REST APIs; generated OpenAPI schemas and status/error responses; production docs denied. | P12 exit evidence: `OpenApiIntegrationTest` passed (2); production route denial passed in `ProductionAuthorizationBoundaryTest`. |
| P1 | RULE — safety, approval, policy | Existing allow-listed Java checks, sandbox/secret guardrails, high-impact approval gates, and no model-generated command execution are covered by code/tests. | Preserve these controls; rerun guardrail and lifecycle tests after API/documentation changes. |
| P1 | AIU — AI/provider use | `LlmProvider` and deterministic mock are implemented; no live model or model-quality evidence exists. | Keep the provider boundary; document the mock-only limitation and do not introduce arbitrary command execution. |
| P1 | AGT — agent orchestration | Persisted DAG, scheduler, task state, retry/fallback/compensation, safe-stop, audit, and replanning exist. | Verify no behavioral regressions with targeted and full suites; retain explicit approval for risky tasks. |
| P1 | TST — test quality/evidence | Latest clean run: 114 tests, 113 passed, no failures/errors, 1 Windows symlink skip; explicit BDD: 6 scenarios / 22 steps passed. | P14 exit evidence is captured in `TEST_RESULTS.md` and `evidence/production-hardening-test-summary.txt`; screenshot evidence remains missing. |
| P1 | PRD — operational readiness | Code-level PostgreSQL/OIDC profile is present, but no live DB/IdP, gateway, abuse, recovery, CVE, penetration, or load evidence. | Keep readiness decision NO-GO; list missing gates and captured evidence status without claiming production readiness. |
| P2 | OWN — ownership and continuity | `.github/copilot-instructions.md` and `memory-bank/` provide linked startup and completion workflows. | P13 exit evidence: manual review against the Memory Bank and canonical documentation. |
| P2 | Release and change control | Commit `cbbbbd2` contains the reviewed changes and required co-author attribution. | P14: pushed normally to `origin/main`; no force-push; post-push status was clean and synchronized. |

## 0. Environment findings
- Workspace was empty and is not a git repo (will `git init`).
- Initially installed: Java 1.8.0_202 and git; Maven and JDK 17/21 were absent.
- Installed in the user profile: Eclipse Temurin JDK 21.0.12.1 and Apache Maven 3.9.11. Spring Boot 3 requires JDK 17 or newer.
- Maven Central metadata check found Spring Boot 3.5.16 as the latest 3.5.x version available during planning; use that compatible 3.x release.
- The current VS Code/tool process still inherits the old system Java 8/Maven-missing PATH. Build commands in this session explicitly prepend the installed JDK/Maven paths; the README includes the equivalent PowerShell setup.
- No build or tests will be reported until actually run on the installed toolchain.

## 1. Requirements

### Functional: URL shortener (FR-U)
- FR-U1 `POST /api/urls` creates a short URL (validated http/https URL, optional custom alias, optional expiry).
- FR-U2 `GET /{code}` returns a 302 redirect; 404 for unknown codes; 410 for expired codes.
- FR-U3 Expiration: `expiresAt`, enforced at redirect time.
- FR-U4 Click analytics: each redirect records a click (timestamp, referrer, user-agent). `GET /api/urls/{code}/stats` returns totals and recent clicks.
- FR-U5 Security: HTTP(S)-only destinations without credentials, DNS-independent rejection of local/private literal addresses and common internal host suffixes, request length limits. Application-level rate limiting is not implemented; production deployments must use gateway controls. Hostname resolution policy remains a deployment responsibility.

### Functional: agent orchestration (FR-A)
- FR-A1 Explicit DAG of tasks (typed nodes, dependencies, cycle detection).
- FR-A2 Task states: PENDING, READY, RUNNING, AWAITING_APPROVAL, SUCCEEDED, FAILED, RETRYING, ROLLED_BACK, SKIPPED, CANCELLED.
- FR-A3 Engine runs independent tasks in parallel (bounded pool) and joins dependents once all prerequisites succeed.
- FR-A4 Sequential mode (parallelism = 1).
- FR-A5 Execution history: append-only event log per run and per task.
- FR-A6 Decision lineage: each decision (plan choice, retry, fallback, approval, replan) records actor (agent/human/policy), inputs, rationale, and parent decision.
- FR-A7 Human approval checkpoints for high-impact tasks (schema change, delete, dependency change, public API change). REST API to approve/reject.
- FR-A8 Bounded retries with backoff, fallback strategy, and rollback (compensations in reverse order).
- FR-A9 Safe-stop: global kill switch and per-run cancel; cooperative interruption and rollback where required.
- FR-A10 Dynamic replanning: on requirement/upstream output change, diff the DAG, invalidate affected downstream tasks, keep unaffected finished ones.
- FR-A11 Agents: Requirements, Architect, Coder, Tester, Reviewer, Security, Documentation. All LLM calls go through `LlmProvider`; default is deterministic `MockLlmProvider`.
- FR-A12 Greenfield, brownfield, and ambiguous scenarios are clearly separated, reproducible, and runnable through REST and CLI. CLI and REST call the same orchestration services.
- FR-A13 Brownfield runs against this URL shortener: capture a passing baseline, then implement a scoped enhancement/bug fix with impacted modules, regression tests, and validation recorded.

### Guardrails (FR-G)
- Agents never execute shell commands or model-generated code. They emit artifacts into a sandboxed workspace directory. Allowed actions are an enum of whitelisted Java-implemented operations (`WRITE_ARTIFACT`, `READ_ARTIFACT`, `RUN_REGISTERED_CHECK`).
- Policy engine: path-traversal protection, secret scanning, size limits, risk classifier (LOW/MEDIUM/HIGH) forcing approval for HIGH.
- Change control: every artifact change is versioned, with a diff summary linked to its decision.
- Compliance: append-only event history with per-run hash chain (tamper evidence; not independently immutable or externally anchored).

### Observability (FR-O)
- Metrics: success rate, retry count, rollback frequency, MTTR (failure to recovery), end-to-end latency (p50/p95), approval wait time.
- Exposed at `/api/metrics/reliability` and via Actuator/Micrometer. Audit trail at `/api/runs/{id}/audit`.

### Non-functional
- Java 21 (17 compatible), Spring Boot 3.x, Maven, Spring Data JPA, H2, JUnit 5, Mockito.
- Runs with no API keys; deterministic outputs.
- Docs: README (setup), ARCHITECTURE, risks/trade-offs/assumptions/limitations.

## 2. Proposed architecture

Single Maven module, strict package boundaries.

```
 REST Controllers (shortener, runs, approvals, metrics)
        |
 OrchestrationService --> Planner --> DagValidator
        |                    ^
        |               Replanner (change detection / diff)
        v
 ExecutionEngine (thread pool, scheduler, state machine)
   |-- RetryPolicy / FallbackResolver / RollbackManager
   |-- ApprovalGate (blocks task until human decision)
   |-- SafeStopController
   |-- GuardrailPipeline (policy, risk, secrets, path sandbox)
   v
 Agent SPI --> LlmProvider (interface) --> MockLlmProvider (default)
        v
 Audit & Observability: EventStore (JPA), DecisionLineage, HashChain, MetricsService
        |
 H2 via Spring Data JPA   <-- URL Shortener domain (independent package)
```

Key decisions:
- The engine is domain-agnostic; the shortener is both the product and the subject the agents "build".
- `Agent.execute(TaskContext)` returns `TaskResult` (artifacts, confidence, risk). Only the engine changes task state.
- Scheduling: `CompletableFuture` on a bounded `ExecutorService`; tasks run when all deps are SUCCEEDED; a single transition lock keeps persistence consistent.
- Rollback: tasks may register a `Compensation`; on unrecoverable failure, succeeded tasks are compensated in reverse topological order.
- Replanning: Replanner computes the transitive dependents of the changed node, resets them, adds/removes nodes, records a REPLAN decision.

## 3. Dependency graph and phased plan

```
P0 toolchain/bootstrap
  -> P1 shortener baseline -> P7 brownfield enhancement/regression demonstration
  -> P2 engine core (DAG, states, history)
       -> P3 execution (parallelism, retry, fallback, rollback, safe-stop)
       -> P4 guardrails + approvals
       -> P5 agents + mock LLM
            -> P6 replanning
                 -> P7 greenfield / brownfield / ambiguous scenarios (REST + CLI)
P2..P7 -> P8 metrics/hash-chained audit -> P9 integration hardening + documentation
```

| Phase | Scope | Exit criteria |
|---|---|---|
| P0 | Install JDK 21 + Maven; initialize git, Maven project and minimal Spring Boot skeleton | absolute-path JDK/Maven versions verified, context loads |
| P1 | Working shortener baseline: entities, service, controllers, validation, expiry, analytics | baseline unit + MockMvc tests run |
| P2 | Task/Dag model, cycle validator, state machine, event store, lineage | DAG and transition unit tests |
| P3 | Parallel scheduler, retries, fallback, rollback, safe-stop | deterministic concurrency tests |
| P4 | Guardrails, risk classifier, approval gate and API | high-risk task blocks until approved |
| P5 | Agent SPI, 7 agents, LlmProvider and mock | deterministic outputs |
| P6 | Replanner and change detection | requirement change invalidates only downstream |
| P7 | Three separate scenarios; brownfield starts with the verified P1 baseline and applies a scoped enhancement with regression coverage; REST and CLI reuse services | scenarios run end to end, artifacts show baseline/change/tests/validation |
| P8 | Metrics, audit export, hash-chain verification | metrics values verified in tests |
| P9 | Integration tests, README, ARCHITECTURE, risks docs, full test run | `mvn verify` executed and reported |
| P10 | Final verification pass and persistent `memory-bank/` handover (REQ-22) | clean `mvn clean verify`; lifecycle, API and security evidence; Memory Bank checked against source and results |
| P11 | Production-profile hardening: PostgreSQL + Flyway, OIDC/JWT issuer/audience validation, scope-based authorization, truthful readiness docs | H2 migration validation and MockMvc security tests pass; live PostgreSQL/OIDC and deployment controls remain unverified |
| P12 | OpenAPI/Swagger contract and safe profile exposure (REQ-24) | Complete; focused integration tests pass for local docs and production denial |
| P13 | Reusable Copilot Agent instructions and Memory Bank/doc alignment (REQ-25) | Complete; startup/update workflow checked in and docs reconciled |
| P14 | Final verification, evidence review, commit and push | Full/BDD tests passed with documented skip; commit `cbbbbd2` pushed normally to the specified remote |

### Phase progress

- **P0 complete:** user-profile Eclipse Temurin JDK 21.0.12.1 and Maven 3.9.11 installed; Spring Boot 3.5.16 Maven project and git repository initialized; context-load test executed successfully (1 test, 0 failures, 0 errors).
- **P1 complete:** URL-shortener baseline implemented in `shortener/` with JPA persistence, create/redirect/stats endpoints, HTTP(S) validation, expiry, and click capture. Full `mvn test` executed with 11 tests, 0 failures, 0 errors, 0 skipped.
- **P2 complete:** typed DAG validation (duplicate/missing dependencies/cycles), run/task JPA persistence, guarded state transitions, event history, and parent-linked decision records are implemented. Full `mvn test` executed with 23 tests, 0 failures, 0 errors, 0 skipped.
- **P3 complete:** typed handlers run through a bounded executor and dependency-aware scheduler; sequential mode, bounded retry/backoff, registered fallbacks, reverse-topological compensation, and per-run/global cooperative safe-stop/resume are implemented. Full `mvn test` executed with 30 tests, 0 failures, 0 errors, 0 skipped.
- **P4 complete:** persisted approval requests and human decisions, REST endpoints for listing/deciding approvals, and dispatch blocking until HIGH-impact tasks are approved. Added typed operation/check allowlists, sandbox path checks (including symlink target validation), secret scanning, risk classification, and versioned artifacts with SHA-256 and decision lineage. Policy denials and approval requirements are written to decision history independently of the failed artifact-write transaction. No model-generated command text is accepted or executed. Validation: full `mvn test` executed with 45 tests, 0 failures, 0 errors, and 1 skipped; the symlink test was skipped because the Windows environment did not permit creating symbolic links.

- **P5 complete:** introduced `LlmProvider` and deterministic no-key `MockLlmProvider`, typed agent requests/results, and the Requirements, Architect, Coder, Tester, Reviewer, Security, and Documentation agents. Seven task handlers route the existing task kinds through the agent registry, preserve run/dependency context, persist parent-linked agent decisions, and send generated proposals through the artifact policy/version service. Agents return structured content only; they never run commands or modify application source directly. Validation: full `mvn test` executed with 50 tests, 0 failures, 0 errors, and 1 skipped (the Windows symlink setup).

- **P6 complete:** dynamic replanning validates an entire replacement DAG, detects changed/added/removed task definitions, updates changed upstream outputs or run requirements, invalidates only affected downstream tasks, and preserves unaffected successful results. Removed tasks remain persisted as retired audit records and are excluded from later execution; retired IDs can be reintroduced. Replanning is blocked during active runs, pending approvals, or safe-stop requests. DAG deltas, state transitions, output hashes, and parent-linked `REPLAN` decisions are persisted. Validation: full `mvn test` executed with 56 tests, 0 failures, 0 errors, and 1 skipped (the Windows symlink setup).

### P7 complete: run APIs and assessment scenarios

- Added run REST APIs for create/get, execute/resume, safe-stop, replan, per-run pending approvals, and event/decision audit export. Invalid run IDs and state conflicts produce explicit API errors.
- Added fixed Java DAG definitions for greenfield, brownfield, and ambiguous requirement scenarios. The REST scenario API and Java `ApplicationRunner` CLI both use `ScenarioService`.
- Brownfield sets a configurable 30-day default URL expiry (explicit expiry overrides it), runs a Java baseline check before the change proposal, blocks the HIGH-impact implementation task for approval, then runs expiry regression validation, review, security, and documentation tasks.
- Ambiguous requirements pause at a HIGH-impact clarification task; the human rationale is passed as context to downstream agents.
- Updated README, architecture and scenario documentation with endpoints, setup/CLI commands, risks, trade-offs, assumptions, and limitations.
- Validation: targeted `mvn -Dtest=ScenarioApiIntegrationTest test` executed with 3 tests, 0 failures, 0 errors, 0 skipped. Full `mvn test` executed with 61 tests, 0 failures, 0 errors, and 1 skipped (Windows symbolic-link setup unavailable).

### P8 complete: reliability metrics and audit integrity

- Every persisted execution event has a per-run sequence, previous hash, and SHA-256 hash over canonical event fields. A pessimistic run-row lock serializes concurrent appends; the run stores a sequence/hash tip anchor so event edits, reordering, and truncation are detectable.
- `GET /api/runs/{runId}/audit/verify` verifies sequence continuity, prior-hash links, event contents, and the run tip anchor. Audit exports now include sequence and hash fields.
- `GET /api/metrics/reliability` reports terminal-run success rate, persisted retry count, rollback count/frequency, failure-to-recovery MTTR, p50/p95 end-to-end latency, and mean/p95 approval wait.
- Added integration coverage for metric definitions, API shape, simultaneous event append serialization, event modification, and truncation detection.
- Validation: P8-focused `mvn -Dtest=AuditMetricsIntegrationTest test` executed with 3 tests, 0 failures, 0 errors, 0 skipped. Final full `mvn test` executed with 64 tests, 0 failures, 0 errors, and 1 skipped (Windows symbolic-link setup unavailable).
- Limitation: the event chain and its tip anchor share the same database and are not signed or externally anchored.

### P9 complete: integration hardening and documentation

- Updated setup, API, architecture, scenario, and risk documentation to reflect the implemented system and its operational constraints.
- Ran final `mvn verify`: **64 tests, 0 failures, 0 errors, 1 skipped**, and `BUILD SUCCESS`. The one skip is the symbolic-link escape test because symlink creation is unavailable in this Windows environment.

### Assessment hardening pass (historical implementation)

- Added Cucumber/JUnit Platform features for shortener create/redirect/analytics/expiry/error behavior and the greenfield, brownfield, and ambiguous orchestration checkpoints. The BDD steps invoke the running Spring application through MockMvc.
- The earlier version used HTTP Basic with an in-memory production operator. P11 supersedes that configuration with OIDC/JWT; old `rest-*.txt` transcripts do not verify the current profile.
- Rejected local/private IP literals, selected non-canonical numeric IP forms, and common local-only hostnames without DNS lookups. Rate limiting and resolver-aware hostname policy remain deployment gateway requirements.
- Serialized run start transitions with a pessimistic database row lock and added duplicate-execution coverage. Added validation/conflict API tests.
- CLI runner supports headless invocations that close their Spring context after emitting the scenario result.
- Added test plan, requirements traceability, test results, readiness, and evidence documentation. Existing `agent-workspace/` outputs remain preserved and ignored.
- Windows symbolic-link test still exercises the escape check when the OS permits link creation; it remains skipped only when creation is denied. Instructions are documented for enabling symlink privilege.
- Hardening-pass validation (superseded by the P10 results below): `mvn clean verify` completed with 97 tests, 0 failures, 0 errors, 1 skipped; BUILD SUCCESS.

### P10 complete: final verification and Memory Bank (2026-10-16; historical test count superseded)

- Reconfirmed the baseline with a clean run (97 tests) before any change, then added `ScenarioLifecycleService` and CLI options `--decision`, `--approver`, `--rationale` and `--safe-stop-before-resume`. Both CLI and REST reuse the same `ApprovalService` and `OrchestrationRunService`; nothing is auto-approved.
- Added 15 tests: scenario approval lifecycle (approve, reject, safe-stop; brownfield and ambiguous), API error contract, production 401/403 boundary, and production fail-closed startup. Cucumber now writes HTML/JSON reports to `target/cucumber-reports/`.
- No production defect was found. Two evidence-script mistakes, PowerShell quote stripping in curl bodies and an unusable embedded-browser screenshot, were corrected or discarded.
- P10 final `mvn clean verify`: **112 tests, 0 failures, 0 errors, 1 skipped; BUILD SUCCESS**. This is historical assessment-baseline evidence only; the current post-P11 result is in [TEST_RESULTS.md](./TEST_RESULTS.md).
- Added `memory-bank/` (8 files plus README). Screenshots were not captured; see `docs/EVIDENCE.md`.

### P11 complete: production-profile hardening (2026-10-16)

- Production configuration now targets PostgreSQL, applies Flyway schema migrations, and uses Hibernate `ddl-auto=validate`; local/test profile remains H2.
- Replaced HTTP Basic with OIDC JWT resource-server authentication, HTTPS issuer validation, required audience validation, and `agentic.operator` scope. Approval attribution uses the JWT subject.
- P11 completion verification at that phase: **111 tests, 0 failures, 0 errors, 1 skipped; BUILD SUCCESS**. The later P14 verification below supersedes those counts.
- Verification boundary: migrations/schema ran against H2 and authorization used MockMvc JWT test tokens. No real PostgreSQL server, OIDC issuer, TLS gateway, rate-limit policy, backups/restore, or live deployment was available. P11 implementation is complete; production readiness is not claimed.

### P12–P14 complete through verification (2026-10-16)

- P12 added springdoc-openapi 2.9.1, endpoint/request documentation, local Swagger UI, production-profile disablement, and explicit production security denial. Focused OpenAPI/security tests: 6 passed.
- P13 added `.github/copilot-instructions.md` and reconciled the Memory Bank, requirement IDs, README, readiness report, and test evidence.
- P14 `mvn clean verify`: **114 tests, 113 passed, 0 failures, 0 errors, 1 skipped; BUILD SUCCESS**. Explicit `mvn -Dtest=RunCucumberTest test`: **6 scenarios / 22 steps passed, 0 failed, 0 skipped**. A packaged-app loopback REST smoke run verified health/docs/create/redirect/analytics/validation; see `evidence/rest-p14-local-checks.txt`.
- Publication completed: commit `cbbbbd2` pushed to the specified `origin/main`; the post-push worktree was clean. Screenshots remain missing. Production NO-GO remains due to unverified live services and operational controls.

## 4. Directory structure
```
schwab-agentic-url-shortener/
├─ pom.xml
├─ README.md
├─ docs/ (plan, architecture, scenarios, risks, test, traceability, readiness, evidence)
├─ evidence/ (real CLI, REST and Maven result transcripts)
├─ memory-bank/ (agent handover; start with memory-bank/README.md)
├─ src/main/java/com/schwab/agentic/
│  ├─ shortener/ (api, config, domain, repository, service)
│  ├─ orchestration/ (api, approval, config, dag, domain, engine, replan, repository)
│  ├─ agents/ and llm/ (typed agents and LlmProvider/mock)
│  ├─ guardrails/ and audit/ (policies, sandbox, artifact versions, hash chain)
│  ├─ scenarios/ (catalog, REST/CLI services, registered Java checks)
│  ├─ metrics/
│  └─ security/ (local and production HTTP security)
├─ src/main/resources/ (application.yml, application-prod.yml)
└─ src/test/ (JUnit/Mockito, Spring integration, security, Cucumber features)
```

## 5. Checklist mapped to assessment criteria

| # | Criterion | Phase | Deliverable | Done |
|---|---|---|---|---|
| 1 | Working shortener | P1 | shortener package + tests | [x] |
| 2 | DAG, states, history, lineage | P2 | dag, lineage, event store | [x] |
| 3 | Sequential/parallel + sync | P3 | Scheduler + concurrency tests | [x] |
| 4 | Human approval checkpoints | P4 | Approval gate, persisted human decisions, REST API | [x] |
| 5 | Retries, fallback, rollback, safe-stop | P3 | resilience package | [x] |
| 6 | Security, compliance, change control | P4/P8 + hardening | policy guardrails, production operator authentication, versioned artifacts, and verifiable event hash chain | [x] |
| 7 | Observability/reliability metrics | P8 | reliability metrics endpoint with latency, retry, rollback, recovery, and approval-wait measures | [x] |
| 8 | Dynamic replanning | P6 | Persisted DAG diff + downstream invalidation tests | [x] |
| 9 | Three separate, reproducible scenarios; brownfield baseline, enhancement, impacted modules, regression tests, validation; REST + CLI | P7 | scenario runners + docs | [x] |
| 10 | Tests + docs | P7/P9 + hardening | README, architecture, scenario, risk, test plan, traceability, results, readiness, evidence, and `mvn clean verify` | [x] |
| 11 | OpenAPI contract and Swagger UI | P12 | Generated API schemas/statuses and production-disabled docs with integration tests | [x] |
| 12 | Reusable Copilot Agent handover | P13 | `.github/copilot-instructions.md` aligned with `memory-bank/` startup/completion workflow | [x] |
| C1 | All Java | all | application and orchestration logic are Java | [x] |
| C2 | LLM behind interface, mock | P5 | LlmProvider | [x] |
| C3 | No arbitrary AI command execution | P4/P5 | typed allowlists, registered-check selection + tests | [x] |
| C4 | No false test claims | P9 | only real `mvn` output reported | [x] |

## 6. Confirmed assumptions and implementation decisions
- Assessment demo uses in-memory H2. Production profile targets PostgreSQL and OIDC JWT; live services/deployment controls remain unverified.
- Base package: `com.schwab.agentic`.
- Brownfield target: this URL shortener; P1 establishes a working, tested baseline before the scenario demonstrates its enhancement or bug fix.
- REST is primary; CLI is an additional interface over the same orchestration services.
- JDK 21 and Maven installation is approved.
- Agents may propose code changes only as sandboxed artifacts. Any application of risky changes requires explicit approval; checks run only through fixed, registered Java operations, never model-generated commands.
- Phases proceed incrementally. Any substantial architectural change beyond this reviewed proposal will be presented for review before implementation.
- PostgreSQL/Flyway and OIDC/JWT configuration exists; live deployment verification, HA/recovery, gateway TLS/rate limiting, resolver-aware host policy, external audit anchoring, CVE scanning, and production operations remain incomplete.
- The OpenAPI description and local Swagger UI are development conveniences, not a production API gateway or a security control; `prod` disables and denies the documentation routes.
