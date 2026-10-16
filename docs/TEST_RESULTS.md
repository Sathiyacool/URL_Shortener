# Test results

These results are local verification records. Environment-specific deployment checks are tracked in [OPERATIONS_AND_RELEASE_READINESS.md](./OPERATIONS_AND_RELEASE_READINESS.md).

Last full source verification: **2026-10-17 00:59:33 +05:30**, rerun after the API smoke assertion changes. The final `mvn -B clean verify` result below supersedes the 114-test OpenAPI baseline.

Follow-up test-phase run after the documentation update: `mvn -B test` on 2026-10-17 completed with **149 tests, 144 passed, 0 failures, 0 errors, and 5 skipped; BUILD SUCCESS**. This run included the Cucumber suite; the latest clean `verify` record and its detailed skip explanations remain the release evidence below.

This run validates the local H2 profile, application tests, and MockMvc security boundaries. PostgreSQL-specific contract tests were **skipped, not passed**, because Docker was unavailable on this host; the corresponding H2 tests ran. OIDC route tests used synthetic MockMvc JWTs and a local-key signature test. GitHub-hosted workflow execution and live identity-provider/database validation are tracked separately in [operational release readiness](./OPERATIONS_AND_RELEASE_READINESS.md).

## Final clean verification

| Field | Result |
|---|---|
| Command | `mvn -B clean verify` |
| Java / Maven / Spring Boot | Eclipse Temurin 21.0.12.1 / 3.9.11 / 3.5.16 |
| Total tests | **149** |
| Passed | **144** |
| Failures / Errors | 0 / 0 |
| Skipped | **5** |
| Outcome | `BUILD SUCCESS` (exit code 0; finished 2026-10-17T00:59:33+05:30) |

The 35-test increase over the 114-test baseline: `ScenarioCliRunnerTest` (10), `OidcLocalKeyValidationTest` (6), `H2PersistenceContractTest` (4), `PostgresPersistenceContractTest` (4, all skipped), `LoadSmokeTest` (1) and 10 additional Cucumber scenarios (`RunCucumberTest` 6 → 16), minus nothing removed. Counts were aggregated from the Surefire XML after the actual clean verification; the final Maven output is [../evidence/maven-verify-final.txt](../evidence/maven-verify-final.txt), and the earlier P15 summary is retained at [../evidence/maven-verify-summary-p15.txt](../evidence/maven-verify-summary-p15.txt).

The five skipped tests:

- `ArtifactSandboxTest.rejectsSymlinkThatTargetsOutsideSandbox`: Windows denied `Files.createSymbolicLink` (`A required privilege is not held by the client`), so its **escape assertion was not executed**. Instructions to enable and rerun it are in [TEST_PLAN.md](./TEST_PLAN.md).
- Four `PostgresPersistenceContractTest` tests: `disabledWithoutDocker is true and Docker is not available`. The identical assertions **did** run against H2 via `H2PersistenceContractTest`, but PostgreSQL itself, its Flyway run and its row-lock behavior remain **unverified until the tests run on a host with Docker** (for example in CI on `ubuntu-latest`).

## BDD (Cucumber 7.25.0)

The BDD selector was run explicitly with `mvn -B -Dtest=RunCucumberTest test` and ran again in the final `mvn -B clean verify`. Both reported 16 tests, 0 failures, 0 errors and 0 skipped. The generated report identifies 3 feature files and 76 steps, all passed. The final report screenshot is [../evidence/screenshots/05-bdd-cucumber-final-report.png](../evidence/screenshots/05-bdd-cucumber-final-report.png). Totals from `target/cucumber-reports/cucumber.json` and Surefire output:

| Measure | Result |
|---|---|
| Features | 3 (`url_shortener`, `orchestration_scenarios`, `orchestration_lifecycle`) |
| Scenarios | 16 passed, 0 failed, 0 skipped |
| Steps | 76 passed, 0 failed, 0 skipped |

Reports are generated in `target/cucumber-reports/` (`cucumber.html`, `cucumber.json`). The new `orchestration_lifecycle.feature` covers: approval granted then resumed to `SUCCEEDED` with a verified audit hash chain; rejection; safe-stop before dispatch then resume; second decision on a decided approval (409); retry exhaustion for a task without a handler (`FAILED`, at most 3 attempts); replan refused while awaiting approval and while a safe-stop is pending (409); replan of a completed run (200); invalid requests (400); and unpublished `env` / `heapdump` actuator endpoints (404). Production-profile security boundaries are covered by JUnit (`Production*Test`), not Cucumber, because the BDD context uses the default profile.

Behaviors the BDD run revealed (documented, not defects): approving the brownfield checkpoint leads to a second approval checkpoint, so BDD approves in a loop; an unknown scenario ID returns 400, not 404; replanning is allowed only from `CREATED`, `SAFE_STOPPED`, `SUCCEEDED` or `ROLLED_BACK` and requires the safe-stop flag to be cleared first, so a REST caller cannot replan a safe-stopped run without resuming it.

## Tests per suite (executed)

| Suite | Tests |
|---|---|
| `AgenticApplicationTest` | 1 |
| `AgentProviderTest` / `AgentTaskHandlerIntegrationTest` | 4 / 1 |
| `ApiErrorContractTest` / `OpenApiIntegrationTest` | 5 / 2 |
| `AuditMetricsIntegrationTest` | 3 |
| `ArtifactSandboxTest` | 4 (3 executed, 1 skipped) |
| `ArtifactVersionServiceIntegrationTest` / `PolicyEngineTest` | 3 / 4 |
| `ApprovalApiIntegrationTest` | 2 |
| `DagValidatorTest` / `RunStateMachineTest` / `TaskStateMachineTest` | 5 / 2 / 3 |
| `ExecutionEngineIntegrationTest` / `OrchestrationPersistenceIntegrationTest` | 10 / 2 |
| `DynamicReplanServiceIntegrationTest` | 6 |
| `ScenarioApiIntegrationTest` / `ScenarioLifecycleIntegrationTest` | 3 / 5 |
| `ScenarioCliRunnerTest` | 10 |
| `ProductionSecurityIntegrationTest` / `ProductionAuthorizationBoundaryTest` / `ProductionFailClosedTest` / `SecurityConfigurationTest` | 2 / 4 / 1 / 2 |
| `OidcLocalKeyValidationTest` | 6 |
| `H2PersistenceContractTest` / `PostgresPersistenceContractTest` | 4 / 4 (all skipped) |
| `LoadSmokeTest` | 1 |
| `ShortUrlApiIntegrationTest` / `ShortUrlServiceTest` | 6 / 28 |
| `RunCucumberTest` | 16 |

## Dependency, secret and load results (real runs)

- OSV dependency scan: the first scan found **19 advisories in 98 runtime artifacts**; after patch-level overrides in `pom.xml` the final rescan shows **0 unaccepted findings and 2 accepted, documented advisories** (`spring-webmvc` 6.2.19). No supported fixed Spring 6.2.x version was available with the Boot 3.5.16 BOM at verification time; applicability and risk treatment are in [SECURITY_SCANNING.md](./SECURITY_SCANNING.md). Output: [../evidence/dependency-scan-osv.txt](../evidence/dependency-scan-osv.txt).
- Local secret scan: 0 unallowed matches, 4 documented fake test fixtures: [../evidence/secret-scan-local.txt](../evidence/secret-scan-local.txt). Git-history scanning (gitleaks) exists only in the CI workflow and has not run.
- Load smoke test (in `mvn verify`): 200 creates, 200 redirects on one link and 16 parallel greenfield runs over real HTTP with 0 failures, and analytics recorded exactly 200 clicks. Numbers and host details: [../evidence/load-test-results.txt](../evidence/load-test-results.txt) and [LOAD_TEST_PLAN.md](./LOAD_TEST_PLAN.md). This is a correctness guard on one 4-CPU Windows laptop, not a capacity benchmark.
- Genuine screenshots: [../evidence/screenshots/](../evidence/screenshots/) includes the final Maven result, generated Cucumber report, and browser captures of actual CLI transcripts. The 40 API-specific screenshots (including the 2026-10-17 direct request/response captures) are indexed in [../evidence/api-tests/test-summary.md](../evidence/api-tests/test-summary.md). That index differentiates captured HTTP responses from test-report screenshots; production authentication capture requires a live test identity provider.

## Non-test executions (real runs, not JUnit)

- Packaged-app local HTTP smoke check: health, OpenAPI, Swagger UI, URL creation (201), redirect (302), click analytics, and invalid-destination error (400) all returned expected results; transcript: [../evidence/rest-p14-local-checks.txt](../evidence/rest-p14-local-checks.txt). This used the unauthenticated loopback profile only.
- API evidence smoke: **64 checks passed, 0 failed** against all 17 OpenAPI operations and generated Swagger/OpenAPI/Actuator routes. Assertions covered response JSON, default expiry, 302 plus expected `Location`, exactly one click after redirect, persisted replan/safe-stop state, lifecycle outcomes, and error response shapes. Script, transcript, route inventory and screenshot index: [evidence/api-tests/README.md](../evidence/api-tests/README.md), [endpoint-inventory.md](../evidence/api-tests/endpoint-inventory.md), [test-summary.md](../evidence/api-tests/test-summary.md). Default profile/H2/loopback only.
- Focused production security suite rerun during API evidence work: **7 tests passed, 0 failures, 0 errors, 0 skipped** (`ProductionAuthorizationBoundaryTest` 4, `ProductionSecurityIntegrationTest` 2, `ProductionFailClosedTest` 1). Synthetic JWTs via MockMvc; not live HTTP or an identity-provider integration.
- Final full Maven output and result screenshot: [maven-verify-final.txt](../evidence/maven-verify-final.txt), [01-maven-verify-final-summary.png](../evidence/screenshots/01-maven-verify-final-summary.png). The explicit BDD selector was also executed; see counts above.
- Eight headless CLI runs from the earlier assessment configuration, including approve, reject and safe-stop lifecycle: [../evidence/](../evidence/) `cli-*.txt`.
- Current production profile with all required environment values empty was launched from the packaged JAR; process exited 1 at startup on missing PostgreSQL configuration: [../evidence/prod-00-fail-closed-database.txt](../evidence/prod-00-fail-closed-database.txt).
- Earlier live production-profile HTTP Basic checks (`rest-00-*`, `rest-01-*`) are **historical and superseded** by the OIDC/PostgreSQL profile. They do not verify the current production profile.
- No PostgreSQL server or live OIDC issuer was available.

## Known warnings

- Mockito prints a JDK dynamic-agent-loading warning on JDK 21. Tests pass; the build does not yet attach Mockito as an explicit agent.
- Cucumber warns that the `features` classpath resource selector should be a package selector. Discovery works; not changed.

## Not covered

- Not tested: PostgreSQL connectivity/backup/restore on a real server, TLS, a live OIDC issuer/JWK rotation, gateway rate limiting, DNS resolver/egress policy, an externally anchored audit service, crash recovery, approval expiry, or production-scale load.
- The `403 Forbidden` response is verified through MockMvc with a synthetic JWT missing the required operator scope; it is not a live IdP token test. See [HARDENING_REVIEW.md](./HARDENING_REVIEW.md) for the full synthetic-versus-live matrix.
- No CI run exists yet; the workflow is untested until it is pushed.
- No live production-auth screenshot exists. The Maven final-summary screenshot and CLI transcript screenshots are genuine captures; they do not substitute for live production evidence or terminal-UI captures.
