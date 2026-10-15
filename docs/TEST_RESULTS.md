# Test results

Last verified: **2026-10-16 (local tool environment, after PostgreSQL/OIDC profile changes)**. This result supersedes the earlier 112-test run.

Production readiness is **not** inferred from this test run. PostgreSQL schema scripts were applied to H2; OIDC route tests used synthetic MockMvc JWTs. Neither a live PostgreSQL service nor a live issuer was exercised.

## Final clean verification

| Field | Result |
|---|---|
| Command | `mvn clean verify` |
| Java / Maven / Spring Boot | Eclipse Temurin 21.0.12.1 / 3.9.11 / 3.5.16 |
| Total tests | **111** |
| Passed | 110 |
| Failures / Errors | 0 / 0 |
| Skipped | **1** |
| Outcome | `BUILD SUCCESS` |

The total is one test lower than the previous run because the Basic-auth-specific invalid-credential test was removed when the production security model changed to OIDC JWTs; its authorization boundary is now covered by anonymous (401), authenticated-without-operator-scope (403), audience-validator unit, and fail-closed configuration tests. The test count is from Surefire XML after the actual clean verification.

The one skipped test is `ArtifactSandboxTest.rejectsSymlinkThatTargetsOutsideSandbox`. Windows denied `Files.createSymbolicLink` (`A required privilege is not held by the client`), so its **escape assertion was not executed**. Instructions to enable and rerun it are in [TEST_PLAN.md](./TEST_PLAN.md).

## BDD (Cucumber 7.25.0)

Run through `RunCucumberTest` (inside `mvn clean verify`, also selectable alone with `mvn -Dtest=RunCucumberTest test`):

| Measure | Result |
|---|---|
| Features | 2 (`url_shortener.feature`, `orchestration_scenarios.feature`) |
| Scenarios | 6 passed, 0 failed, 0 skipped |
| Steps | 22 passed, 0 failed, 0 skipped |

Reports are generated in `target/cucumber-reports/` (`cucumber.html`, `cucumber.json`). The current summary is in [../evidence/production-hardening-test-summary.txt](../evidence/production-hardening-test-summary.txt); [../evidence/maven-final-summary.txt](../evidence/maven-final-summary.txt) is historical P10 evidence.

BDD covers the shortener journeys and each scenario up to its first approval checkpoint. Full approve/reject/safe-stop lifecycles are covered by JUnit (`ScenarioLifecycleIntegrationTest`), not by Cucumber.

## Tests per suite (executed)

| Suite | Tests |
|---|---|
| `AgenticApplicationTest` | 1 |
| `AgentProviderTest` / `AgentTaskHandlerIntegrationTest` | 4 / 1 |
| `ApiErrorContractTest` | 5 |
| `AuditMetricsIntegrationTest` | 3 |
| `ArtifactSandboxTest` | 4 (3 executed, 1 skipped) |
| `ArtifactVersionServiceIntegrationTest` / `PolicyEngineTest` | 3 / 4 |
| `ApprovalApiIntegrationTest` | 2 |
| `DagValidatorTest` / `RunStateMachineTest` / `TaskStateMachineTest` | 5 / 2 / 3 |
| `ExecutionEngineIntegrationTest` / `OrchestrationPersistenceIntegrationTest` | 10 / 2 |
| `DynamicReplanServiceIntegrationTest` | 6 |
| `ScenarioApiIntegrationTest` / `ScenarioLifecycleIntegrationTest` | 3 / 5 |
| `ProductionSecurityIntegrationTest` / `ProductionAuthorizationBoundaryTest` / `ProductionFailClosedTest` / `SecurityConfigurationTest` | 2 / 3 / 1 / 2 |
| `ShortUrlApiIntegrationTest` / `ShortUrlServiceTest` | 6 / 28 |
| `RunCucumberTest` | 6 |

## Non-test executions (real runs, not JUnit)

- Eight headless CLI runs from the earlier assessment configuration, including approve, reject and safe-stop lifecycle: [../evidence/](../evidence/) `cli-*.txt`.
- Current production profile with all required environment values empty was launched from the packaged JAR; process exited 1 at startup on missing PostgreSQL configuration: [../evidence/prod-00-fail-closed-database.txt](../evidence/prod-00-fail-closed-database.txt).
- Earlier live production-profile HTTP Basic checks (`rest-00-*`, `rest-01-*`) are **historical and superseded** by the OIDC/PostgreSQL profile. They do not verify the current production profile.
- This run executed `mvn clean verify`; OIDC authorization/fail-closed tests and the Flyway migration/schema validation ran against H2. No PostgreSQL server or live OIDC issuer was available.

## Known warnings

- Mockito prints a JDK dynamic-agent-loading warning on JDK 21. Tests pass; the build does not yet attach Mockito as an explicit agent.
- Cucumber warns that the `features` classpath resource selector should be a package selector. Discovery works; not changed.

## Not covered

- No dependency vulnerability scan was configured or run; `mvn verify` is not a CVE scan.
- Not tested: PostgreSQL connectivity/backup/restore, TLS, a live OIDC issuer/JWK rotation, gateway rate limiting, DNS resolver/egress policy, an externally anchored audit service, crash recovery, or production-like load.
- The `403 Forbidden` response is verified through MockMvc with a synthetic JWT missing the required operator scope; it is not a live IdP token test.
- `ScenarioCliRunner` argument parsing has no dedicated unit test; it was exercised only by the real CLI runs.
