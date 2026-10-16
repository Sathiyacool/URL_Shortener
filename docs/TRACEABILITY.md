# Requirements traceability

Canonical requirement IDs (`REQ-nn`) used by every document in this repository, including `memory-bank/`.

**Status legend** (verified against source and the 2026-10-16 clean `mvn clean verify`: 114 tests, 0 failures, 0 errors, 1 skipped):

- **Implemented**: code exists in `src/main`.
- **Tested**: an automated test that executed and passed exercises the behavior. A skipped test does not count.
- **Partial**: implemented with a documented boundary, or only part of the requirement is evidenced.
- **Not implemented / Not evidenced**: absent, or no genuine evidence exists.

| ID | Requirement | Implementation | Executed verification | Status |
|---|---|---|---|---|
| REQ-01 | Short URL creation and custom aliases | `ShortUrlController`, `ShortUrlService` | `ShortUrlServiceTest`, `ShortUrlApiIntegrationTest`, `url_shortener.feature` | Implemented, Tested |
| REQ-02 | Redirect, expiration, click analytics | `RedirectController`, `ShortUrlService`, JPA repositories | `ShortUrlApiIntegrationTest`, `ShortUrlServiceTest`, BDD, live loopback/prod checks (`evidence/rest-01-*`) | Implemented, Tested; H2 is in-memory so data is ephemeral |
| REQ-03 | Destination validation | `ShortUrlService` (scheme, credentials, local/private literals, local suffixes, numeric IP forms; no DNS) | parameterized `ShortUrlServiceTest`, API tests, live checks | Implemented, Tested; DNS rebinding and hostnames that resolve to private IPs are **Not implemented** |
| REQ-04 | Explicit dependency DAG and task states | `orchestration/dag`, `orchestration/domain`, state machines | `DagValidatorTest`, `TaskStateMachineTest`, `RunStateMachineTest`, `OrchestrationPersistenceIntegrationTest`, `ApiErrorContractTest` (cycle rejection) | Implemented, Tested |
| REQ-05 | Execution history and decision lineage | `OrchestrationPersistenceService`, audit entities/APIs | `OrchestrationPersistenceIntegrationTest`, `AuditMetricsIntegrationTest`, `ScenarioLifecycleIntegrationTest` (audit verification) | Implemented, Tested |
| REQ-06 | Parallel execution and dependency synchronization | `ExecutionEngine` | `ExecutionEngineIntegrationTest.runsIndependentTasksInParallelAndWaitsForDependencies`, `rejectsConcurrentExecutionOfAnAlreadyRunningRun` | Implemented, Tested; single process only |
| REQ-07 | Sequential execution | `ExecutionEngine`, `ExecutionMode` | `ExecutionEngineIntegrationTest.supportsSequentialExecutionWithOneActiveTask` | Implemented, Tested |
| REQ-08 | Human approval gates (pending, approve, reject, resume) | `ApprovalService`, `ApprovalController`, `ExecutionEngine`, `ScenarioLifecycleService` | `ApprovalApiIntegrationTest`, `ExecutionEngineIntegrationTest` (approval/rejection), `ScenarioApiIntegrationTest`, `ScenarioLifecycleIntegrationTest`, CLI transcripts | Implemented, Tested |
| REQ-09 | Bounded retries and fallback | `RetryPolicy`, `TaskFallbackHandler` | `ExecutionEngineIntegrationTest.retriesOnlyUpToConfiguredLimit...`, `usesRegisteredFallback...` | Implemented, Tested |
| REQ-10 | Rollback / compensation | compensation in `ExecutionEngine` | `ExecutionEngineIntegrationTest.compensatesSucceededTasksInReverseTopologicalOrder...` | Implemented, Tested; compensates registered handlers only |
| REQ-11 | Safe-stop and recovery/resume | `SafeStopController`, `OrchestrationRunService.resume` | `ExecutionEngineIntegrationTest` (safe-stop, global stop), `ScenarioApiIntegrationTest`, `ScenarioLifecycleIntegrationTest` | Implemented, Tested; cooperative stop. Crash recovery and distributed leases are **Not implemented** |
| REQ-12 | Guardrails: sandbox, policy, secret scan, change control | `PolicyEngine`, `ArtifactSandbox`, `ArtifactVersionService`, `SecretScanner` | `PolicyEngineTest`, `ArtifactSandboxTest` (3 executed, 1 skipped), `ArtifactVersionServiceIntegrationTest` | Implemented; symlink-escape assertion **not executed** on this host (Partial) |
| REQ-13 | Production API security (authN/authZ, fail-closed) | `SecurityConfiguration`, `ProductionConfiguration`, `application-prod.yml` | MockMvc JWT scope boundaries (401/403), HTTPS issuer/audience validation, missing OIDC configuration fail-closed, real process exit 1 for missing PostgreSQL env, Flyway/H2 schema validation | Partial: app configured for OIDC/JWT and PostgreSQL; no live issuer or database test, no TLS gateway, rate limiting, identity lifecycle, or production deployment evidence |
| REQ-14 | Reliability metrics: success rate, retries, rollback frequency, MTTR, latency | `ReliabilityMetricsService`, `/api/metrics/reliability` | `AuditMetricsIntegrationTest` | Implemented, Tested; in-process aggregates only |
| REQ-15 | Audit-grade, tamper-evident history | `AuditHashService`, `AuditIntegrityService` | `AuditMetricsIntegrationTest` (tamper and truncation), lifecycle tests verify chain | Implemented, Tested; anchor is in the same database (Partial) |
| REQ-16 | Dynamic replanning | `DynamicReplanService` | `DynamicReplanServiceIntegrationTest` (6) | Implemented, Tested |
| REQ-17 | Greenfield, brownfield, ambiguous scenarios | `ScenarioCatalog`, `ScenarioService`, REST and CLI | `ScenarioApiIntegrationTest`, `orchestration_scenarios.feature`, `ScenarioLifecycleIntegrationTest`, CLI transcripts in `evidence/` | Implemented, Tested |
| REQ-18 | LLM behind an interface with deterministic mock | `llm/LlmProvider`, `MockLlmProvider` | `AgentProviderTest`, `AgentTaskHandlerIntegrationTest` | Implemented, Tested; no real provider |
| REQ-19 | Never execute model-generated commands | allow-listed Java tasks/checks; artifact-only agent output | `PolicyEngineTest.onlyAllowsRegisteredCheckIdentifiers`, architecture review | Implemented by design; no shell API exists |
| REQ-20 | REST primary interface plus CLI sharing the same services | controllers, `ScenarioCliRunner`, `ScenarioLifecycleService` | `ApiErrorContractTest`, CLI transcripts | Implemented, Tested (CLI path via service tests and executed transcripts; no dedicated `ScenarioCliRunner` unit test) |
| REQ-21 | Documentation, setup, risks, trade-offs, limitations | README and `docs/` | document review (this pass) | Implemented |
| REQ-22 | Memory Bank for agent handover | `memory-bank/` | consistency review against source and test results | Implemented |
| REQ-23 | Screenshot evidence | none | none | **Not evidenced**: no usable screenshots captured. See `EVIDENCE.md` |
| REQ-24 | Generated API contract and safe documentation exposure | `OpenApiConfiguration`, annotated REST controllers/DTOs, profile configuration, `SecurityConfiguration` | `OpenApiIntegrationTest` verifies paths, schemas, statuses, auth scheme and local UI; `ProductionAuthorizationBoundaryTest` verifies production routes are denied | Implemented, Tested; generated documentation is local-profile-only |
| REQ-25 | Reusable Copilot Agent startup and completion workflow | `.github/copilot-instructions.md`, `memory-bank/README.md` | Manual consistency review against the requested handover workflow | Implemented |

Production deployment readiness is **not** claimed. See [ASSESSMENT_READINESS.md](./ASSESSMENT_READINESS.md).

PostgreSQL/OIDC configuration and H2/MockMvc tests satisfy only the code/configuration portion of REQ-13; no live PostgreSQL, OIDC issuer, TLS gateway, abuse-control, or deployment evidence exists.
