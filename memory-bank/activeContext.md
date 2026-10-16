# Active context

Date of last verification: **2026-10-16**.

## Last verified results (executed, not assumed)

| Check | Result |
|---|---|
| Latest `mvn clean verify` after OpenAPI and Copilot-instruction changes | **114 tests, 0 failures, 0 errors, 1 skipped, BUILD SUCCESS** (113 passed) |
| Explicit BDD (`mvn -Dtest=RunCucumberTest test`) | **6 scenarios, 22 steps; 0 failed, 0 skipped** |
| OpenAPI-focused tests | 2 generated-contract/UI tests passed; production docs denial covered by production security test |
| Current live local REST check | Packaged app: health/docs 200, create 201, redirect 302, click count 1, invalid destination 400 (`evidence/rest-p14-local-checks.txt`) |
| Skipped test | `ArtifactSandboxTest.rejectsSymlinkThatTargetsOutsideSandbox` (Windows denied symlink creation; assertion not executed) |
| CLI (8 headless runs, `evidence/cli-01..08`) | greenfield SUCCEEDED; brownfield and ambiguous pending-only, approve gives SUCCEEDED, reject gives CANCELLED; brownfield approve plus safe-stop gives SAFE_STOPPED, resumed, SUCCEEDED |
| Current production authorization | MockMvc JWT tests: anonymous 401, missing operator scope 403, operator scope permitted; HTTPS issuer/audience validator unit tests; missing OIDC config fails closed |
| Flyway schema | Full suite applies migration and Hibernate validates entity mappings against H2; no live PostgreSQL server tested |
| Production DB fail-closed process | Current packaged JAR exited 1 when DB/OIDC env vars were empty and reported missing DB/TLS settings (`evidence/prod-00-fail-closed-database.txt`) |
| Earlier live REST checks | `evidence/rest-*.txt` are real but cover the old HTTP Basic profile; historical only, not evidence for current OIDC/PostgreSQL profile |

Sources: [docs/TEST_RESULTS.md](../docs/TEST_RESULTS.md), `evidence/production-hardening-test-summary.txt`.

## Current state

P0 to P13 assessment, production-profile hardening, OpenAPI/Swagger, and Copilot handover phases complete. P14 verification is complete; commit/push review is pending as documented in [docs/PLAN.md](../docs/PLAN.md). Check Git history and worktree for the actual publication state.

Post-P10 changes (2026-10-16): SLF4J logging and execution-flow Mermaid diagram added. Production profile changed from in-memory Basic auth/H2 to OIDC JWT + PostgreSQL/Flyway. P12 adds springdoc-openapi 2.9.1; focused OpenAPI and production-security tests passed (6, 0 failures/errors/skips). Final `mvn clean verify`: 114 tests, 113 passed, 0 failures, 0 errors, 1 skipped; standalone BDD: 6 scenarios / 22 steps passed. Tests use H2 and mock JWT tokens; no live PostgreSQL or OIDC integration.

## Unresolved issues and gaps

- Symlink escape assertion not executed on this machine.
- **Screenshots not captured** (the embedded browser rendering was unusable and was discarded). Manual Windows steps are in [docs/EVIDENCE.md](../docs/EVIDENCE.md). REQ-23 is "Not evidenced".
- 403 is verified only with a MockMvc JWT missing the operator scope, not with a live IdP token.
- No dependency/CVE scan; no `ScenarioCliRunner` unit test (covered by real CLI runs and `ScenarioLifecycleIntegrationTest`).
- No live PostgreSQL/IdP test, backup/restore, crash recovery or distributed leases; audit hash anchor in same DB; mock LLM only.
- **Not production ready**: TLS and rate limits depend on a gateway; no live integration/deployment, vulnerability scan, load/penetration test, or operational recovery validation.

## Next recommended actions

1. Inspect the complete diff and confirm no secret, generated build artifact, or unrelated file is staged; fetch/check `origin` before the authorized commit/push.
2. Capture screenshots manually per `docs/EVIDENCE.md`; enable symlink privilege and rerun the symlink test when feasible.
3. Validate migration and OIDC config against live PostgreSQL and an issuer-issued JWT in a controlled test environment; run dependency, load, penetration, backup/restore, and recovery checks.
