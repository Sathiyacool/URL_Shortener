# Test plan

## Purpose and scope

Verify the URL shortener, persisted orchestration engine, production security profile, and assessment scenarios using JUnit 5, Mockito, Spring integration tests, and Cucumber. Tests use the deterministic mock LLM and in-memory H2; no external model key or network destination is required.

**Readiness boundary:** production configuration targets PostgreSQL and OIDC/JWT, but executed tests use H2, MockMvc synthetic JWT postprocessors and a local-RSA-key validator simulation. The PostgreSQL Testcontainers test is skipped without Docker. A passing suite does not certify a live database, identity provider, gateway, or production deployment.

## Test layers

| Layer | Coverage | Main selectors |
|---|---|---|
| Unit | URL policy/expiry, DAG validation, state machines, agents, guardrails, OIDC and PostgreSQL configuration validators | `ShortUrlServiceTest`, `DagValidatorTest`, `TaskStateMachineTest`, `RunStateMachineTest`, `AgentProviderTest`, `PolicyEngineTest`, `SecurityConfigurationTest` |
| Spring/API integration | URL create/redirect/stats/expiry/errors, approvals, run API, audit/metrics, dynamic replanning, artifact versioning, production OIDC authorization, API error contract, scenario approval lifecycle, Flyway migration/schema validation | `ShortUrlApiIntegrationTest`, `ApprovalApiIntegrationTest`, `ScenarioApiIntegrationTest`, `ScenarioLifecycleIntegrationTest`, `ApiErrorContractTest`, `ProductionSecurityIntegrationTest`, `ProductionAuthorizationBoundaryTest`, `ProductionFailClosedTest`, `AuditMetricsIntegrationTest` |
| Orchestration integration | Dependency synchronization, sequential/parallel dispatch, retries, fallback, compensation, safe-stop, approval, duplicate execution | `ExecutionEngineIntegrationTest`, `OrchestrationPersistenceIntegrationTest` |
| OpenAPI contract | Generated endpoint paths, schemas, status/error responses, bearer scheme, local Swagger UI, and production denial | `OpenApiIntegrationTest`, `ProductionAuthorizationBoundaryTest` |
| BDD | Shortener journeys; scenario checkpoints; orchestration lifecycle (approve, reject, safe-stop/resume, double decision, retry exhaustion, replan guards, invalid requests, hidden actuator endpoints) through MockMvc, default profile | `RunCucumberTest`, `src/test/resources/features/*.feature` (3 features, 16 scenarios) |
| CLI | `ScenarioCliRunner` argument parsing and dispatch with mocked services | `ScenarioCliRunnerTest` |
| Persistence contract | Flyway history, schema validation, persisted run with audit chain, exactly-once concurrent execution. Runs on H2 always; on PostgreSQL 16 via Testcontainers only when Docker is available | `AbstractPersistenceContract`, `H2PersistenceContractTest`, `PostgresPersistenceContractTest` |
| JWT validation (local simulation) | Signature, expiry, issuer, audience and tamper rejection with throwaway RSA keys; not a live IdP | `OidcLocalKeyValidationTest` |
| Load smoke | 200 creates, 200 redirects, 16 parallel runs over real HTTP; exact click count | `LoadSmokeTest`; plan in [LOAD_TEST_PLAN.md](./LOAD_TEST_PLAN.md) |
| Supply chain | OSV dependency scan, secret scan; CI-only gitleaks | `scripts/osv-scan.ps1`, `scripts/secret-scan.ps1`; [SECURITY_SCANNING.md](./SECURITY_SCANNING.md) |
| CI | `mvn -B clean verify` plus report upload on push/PR | `.github/workflows/ci.yml` (hosted execution evidence to be recorded) |
| Full build | Compile, all test layers, packaging and Maven verification lifecycle | `mvn clean verify` |

## Acceptance checks

1. URL API returns expected create/redirect/expiry/error statuses; click analytics records successful redirects only.
2. Unsupported schemes, embedded credentials, local/private literal destinations, local-only hostname suffixes, and numeric IP variants are rejected before persistence. Validation must not resolve hostnames.
3. Production profile returns `401` for anonymous protected routes, returns `403` for an authenticated JWT without `agentic.operator`, accepts a JWT matching the configured issuer/audience and scope, attributes approval decisions to the JWT subject, and leaves public create/redirect behavior available. Health remains public.
4. A running orchestration run cannot be executed a second time concurrently; dependency output is available only after prerequisites succeed.
5. BDD covers all three scenario gates and shortener behavior against real Spring application components.
6. `mvn clean verify` exits successfully. Report actual run/failure/error/skip counts; do not treat an environment-dependent skip as a passed assertion.
7. CLI scenario commands print their real status. Without `--decision`, brownfield and ambiguous runs stop at their approval. With `--decision=APPROVE|REJECT --approver=<name>` the CLI completes (or cancels) the run and, with `--safe-stop-before-resume`, demonstrates `SAFE_STOPPED` then resume.
8. Production profile fails to start without a valid HTTPS OIDC issuer and audience; anonymous requests return `401`; a token without operator scope returns `403`; application errors return `400/404/409` with problem details.
9. Flyway applies the initial schema and Hibernate validates mappings in H2 tests. `PostgresPersistenceContractTest` runs the same checks on PostgreSQL 16 when Docker is available (for example the CI `ubuntu-latest` runner); it was skipped on the author machine. A Docker-enabled run will establish the PostgreSQL-specific result.
10. The generated OpenAPI document contains the implemented REST operations and documented request/error shapes; the default profile serves Swagger UI while the production profile denies both documentation routes.
11. Every discovered controller operation and generated OpenAPI/Swagger/Actuator route is inventoried and exercised by the local HTTP smoke script or a referenced automated test. The API evidence index maps each retained screenshot to its actual endpoint/result and records production-only checks as environment-specific when infrastructure is unavailable.

## Windows symbolic-link test

`ArtifactSandboxTest.rejectsSymlinkThatTargetsOutsideSandbox` attempts to create an actual link and then verifies that resolution outside the sandbox is rejected. On Windows, `Files.createSymbolicLink` may throw when the current user lacks the `SeCreateSymbolicLinkPrivilege` or Developer Mode is disabled. The test uses a JUnit assumption only for that operating-system setup failure; it does not skip the escape assertion after a link is created.

To execute the escape assertion on Windows:

1. Enable **Settings â†’ System â†’ For developers â†’ Developer Mode**, or run the test terminal with an account that has symbolic-link creation privilege.
2. Open a new terminal after changing the setting.
3. Run `mvn -Dtest=ArtifactSandboxTest test`.
4. Confirm Surefire reports four executed tests with no skip.

This project does not silently substitute a junction or mock for a symbolic link. If policy prohibits enabling symlinks, preserve the skip reason in the test report and run the same test on a filesystem/OS that supports link creation.

## Manual demo/evidence checks

- Start the default profile and verify it responds only on loopback.
- Create a short URL, follow it, and inspect click analytics.
- Run each CLI scenario in headless mode; verify greenfield completes and the other two report their approval task.
- Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\api-evidence-smoke.ps1` against the packaged app on `127.0.0.1:8099`; the script asserts response statuses and shapes, required JSON values, redirect `Location`, click-count increment, and persisted orchestration states. Inspect the route/screenshot mapping in `evidence\api-tests\endpoint-inventory.md` and `test-summary.md`. The recorded 64/64 run used the default profile and H2.
- For the production profile, set PostgreSQL connection values and OIDC issuer/audience using a secret manager or protected process environment. Validate protected routes using a live issuer-issued JWT over the TLS gateway; MockMvc test tokens do not prove live identity-provider integration.
- API screenshots and their capture/evidence boundaries are indexed in `evidence/api-tests/test-summary.md`. Production-profile response screenshots remain blocked until a live test identity provider and PostgreSQL environment are available. The final Maven result and browser captures of real CLI transcripts are in `evidence/screenshots/`; direct CLI terminal-window captures remain missing (see `EVIDENCE.md` for steps). Never include production secrets, generated UUIDs from unrelated runs, or user data in screenshots.
