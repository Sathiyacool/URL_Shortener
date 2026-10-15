# Test strategy

Detail: [docs/TEST_PLAN.md](../docs/TEST_PLAN.md); results: [docs/TEST_RESULTS.md](../docs/TEST_RESULTS.md); IDs: [docs/TRACEABILITY.md](../docs/TRACEABILITY.md).

Production readiness is not established by this test strategy or the passing suite; live PostgreSQL, OIDC, TLS gateway, and operational recovery remain unverified.

## Pyramid (111 tests at 2026-10-16)

- **Unit** (plain JUnit/Mockito): `ShortUrlServiceTest`, `DagValidatorTest`, state machine tests, `PolicyEngineTest`, `AgentProviderTest`, `ArtifactSandboxTest`, `SecurityConfigurationTest`.
- **Integration** (`@SpringBootTest`, H2, MockMvc): shortener API, execution engine, replanning, audit/metrics, persistence, approvals, scenarios and lifecycle, API error contract, OIDC scope authorization boundary, fail-closed context, Flyway migration/schema validation.
- **BDD** (Cucumber, `RunCucumberTest`): 6 scenarios, 22 steps, features under `src/test/resources/features`. Behavior-level acceptance of the shortener and orchestration flows; reports in `target/cucumber-reports/`.
- **Manual/live evidence**: CLI and curl transcripts in `evidence/`.

## Responsibilities

JUnit covers detailed logic, concurrency and security boundaries; BDD covers readable end-to-end behavior. Neither replaces the other.

## Coverage expectations

Every `REQ-nn` has at least one executable test or is explicitly marked Partial / Not evidenced. No coverage percentage is measured (not tracked; do not claim one).

## Test data

In-memory H2 per test context, deterministic mock LLM, throwaway credentials set only in test or process environment, sandboxed temp artifact directories.

## Security tests

401 for anonymous requests, 403 for JWTs without required scope (MockMvc), OIDC config fail-closed, HTTPS issuer/audience validator, PostgreSQL URL/credential/TLS-mode validation, validation and error contract, SSRF-style destination rejection, sandbox path escape (symlink case skipped on Windows without privilege). No live OIDC or PostgreSQL integration is asserted by this suite.

## Evidence rules

Record only executed results with date and counts. Report skipped tests. Never fabricate screenshots; missing ones are listed in `docs/EVIDENCE.md`. After any change run `mvn clean verify` and update `activeContext.md`, `progress.md`, and `docs/TEST_RESULTS.md`.
