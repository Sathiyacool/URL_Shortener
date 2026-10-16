# Test strategy

Detail: [docs/TEST_PLAN.md](../docs/TEST_PLAN.md); results: [docs/TEST_RESULTS.md](../docs/TEST_RESULTS.md); IDs: [docs/TRACEABILITY.md](../docs/TRACEABILITY.md).

Passing local tests establish only the behaviors exercised by those tests. Live PostgreSQL, OIDC, TLS gateway, and operational recovery evidence is tracked separately in `docs/OPERATIONS_AND_RELEASE_READINESS.md`.

## Pyramid (149 tests at 2026-10-17: 144 passed, 5 skipped)

P15 additions: `ScenarioCliRunnerTest`, `OidcLocalKeyValidationTest` (local RSA keys, simulation only), persistence contract (`H2PersistenceContractTest` runs; `PostgresPersistenceContractTest` needs Docker and was skipped), `LoadSmokeTest`, OSV/secret scan scripts, CI workflow (never run).

- **Unit** (plain JUnit/Mockito): `ShortUrlServiceTest`, `DagValidatorTest`, state machine tests, `PolicyEngineTest`, `AgentProviderTest`, `ArtifactSandboxTest`, `SecurityConfigurationTest`.
- **Integration** (`@SpringBootTest`, H2, MockMvc): shortener API, execution engine, replanning, audit/metrics, persistence, approvals, scenarios and lifecycle, API error contract and generated OpenAPI contract, OIDC scope authorization boundary, production Swagger/OpenAPI denial, fail-closed context, Flyway migration/schema validation.
- **BDD** (Cucumber, `RunCucumberTest`): 16 scenarios, 76 steps (incl. lifecycle: approve/reject/safe-stop/retry exhaustion/replan/invalid requests), features under `src/test/resources/features`. Behavior-level acceptance of the shortener and orchestration flows; reports in `target/cucumber-reports/`.
- **Manual/live evidence**: CLI and curl transcripts in `evidence/`; `scripts/api-evidence-smoke.ps1` exercises all 17 OpenAPI operations plus generated Swagger/OpenAPI and Actuator routes. Latest recorded API smoke: 64 passed, 0 failed, with JSON, redirect `Location`, click-count and persisted lifecycle assertions (local profile/H2 only); route mapping and screenshot provenance live in `evidence/api-tests/`. Direct redirect-302 (curl.exe) and orchestration response-panel screenshots were captured 2026-10-17 (40 API PNGs); live production-auth screenshots remain blocked.

## Responsibilities

JUnit covers detailed logic, concurrency and security boundaries; BDD covers readable end-to-end behavior. Neither replaces the other.

## Coverage expectations

Every `REQ-nn` has at least one executable test or is explicitly marked Partial / Not evidenced. No coverage percentage is measured (not tracked; do not claim one).

## Test data

In-memory H2 per test context, deterministic mock LLM, throwaway credentials set only in test or process environment, sandboxed temp artifact directories.

## Security tests

401 for anonymous requests, 403 for JWTs without required scope (MockMvc), OIDC config fail-closed, HTTPS issuer/audience validator, PostgreSQL URL/credential/TLS-mode validation, validation and error contract, SSRF-style destination rejection, sandbox path escape (symlink case skipped on Windows without privilege). No live OIDC or PostgreSQL integration is asserted by this suite.

OpenAPI integration checks verify generated endpoint paths, request/response schemas and status codes, local Swagger UI availability, and bearer-auth documentation. Production authorization tests verify that an operator token still cannot access the disabled OpenAPI or Swagger routes.

## Evidence rules

Record only executed results with date and counts. Report skipped tests. Never fabricate screenshots; the API screenshot index is `evidence/api-tests/test-summary.md`; a final Maven summary, Cucumber report, and browser captures of real CLI transcripts are in `evidence/screenshots/`. Direct CLI terminal UI and live production-auth screenshots remain missing/blocked and are documented in `docs/EVIDENCE.md`. After any change run `mvn clean verify` and update `activeContext.md`, `progress.md`, and `docs/TEST_RESULTS.md`.
