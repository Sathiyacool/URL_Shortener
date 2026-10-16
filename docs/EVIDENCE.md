# Assessment evidence index

Last updated: 2026-10-17 after the final clean verification and semantic API smoke run. Requirement IDs refer to [TRACEABILITY.md](./TRACEABILITY.md). Everything below is real output; nothing is fabricated.

Deployment prerequisites and outstanding environment-specific evidence are summarized in [DEPLOYMENT.md](./DEPLOYMENT.md) and [OPERATIONS_AND_RELEASE_READINESS.md](./OPERATIONS_AND_RELEASE_READINESS.md).

## Automated evidence

| Evidence | Actual result | Location | Requirements |
|---|---|---|---|
| Final clean Maven verification (2026-10-17) | `mvn -B clean verify`: 149 tests, 144 passed, 0 failures, 0 errors, 5 skipped; `BUILD SUCCESS` (exit code 0, finished 00:59:33 +05:30). Skips: 1 Windows symlink, 4 PostgreSQL/Testcontainers (no Docker) | [complete latest Maven output](../evidence/maven-verify-final.txt); the [genuine summary screenshot](../evidence/screenshots/01-maven-verify-final-summary.png) is from the prior 2026-10-16 successful run with the same test/skip counts. Per-suite reports are regenerated under ignored `target/surefire-reports/`. Earlier P15 summary: [maven-verify-summary-p15.txt](../evidence/maven-verify-summary-p15.txt) | all |
| Dependency scan | 19 advisories found, patched by version overrides; latest rescan: 98 runtime artifacts, 0 unaccepted, 2 accepted critical findings on Spring Framework 6.2.19. No supported fixed Spring 6.2.x version was available with Boot 3.5.16 at verification; the findings remain visible and accepted. | [dependency-scan-osv.txt](../evidence/dependency-scan-osv.txt), [SECURITY_SCANNING.md](./SECURITY_SCANNING.md) | REQ-27 |
| Secret scan (working tree) | 0 unallowed, 4 documented fake fixtures. Git history **not** scanned | [secret-scan-local.txt](../evidence/secret-scan-local.txt) | REQ-27 |
| Load smoke test | 0 failures across 416 requests; 200/200 clicks recorded; 4-CPU Windows host, H2, default profile | [load-test-results.txt](../evidence/load-test-results.txt), [LOAD_TEST_PLAN.md](./LOAD_TEST_PLAN.md) | REQ-28 |
| OIDC verification matrix | Synthetic-JWT and local-RSA-key tests only; live IdP items listed | [HARDENING_REVIEW.md](./HARDENING_REVIEW.md) | REQ-13 |
| CI workflow | Committed ([ci.yml](../.github/workflows/ci.yml)); GitHub-hosted execution evidence has not been recorded | none | REQ-26 |
| PostgreSQL migration/schema mapping | Flyway migration and Hibernate `validate` run successfully against H2 (`H2PersistenceContractTest`). The same contract on PostgreSQL (`PostgresPersistenceContractTest`) was **skipped: no Docker** | Surefire reports | REQ-13 (partial) |
| OIDC authorization boundaries | Anonymous 401, JWT without operator scope 403, configured operator scope permitted, missing OIDC issuer/audience fails closed; 7 focused production tests passed (plus 2 `SecurityConfigurationTest` unit tests in the full suite) | Surefire XML for `Production*Test` and `SecurityConfigurationTest` | REQ-13 |
| Production database fail-closed startup | Real Java process exited 1 with all DB/OIDC environment variables empty and rejected the missing PostgreSQL configuration before starting | [prod-00-fail-closed-database.txt](../evidence/prod-00-fail-closed-database.txt) | REQ-13 |
| Current local REST smoke check | Packaged application over loopback: health 200, OpenAPI/Swagger 200, create 201, redirect 302, analytics click count 1, invalid scheme 400 | [rest-p14-local-checks.txt](../evidence/rest-p14-local-checks.txt); default profile only, no authentication | REQ-01, 02, 24 |
| Full local API endpoint smoke | **64 checks passed, 0 failed** against all 17 OpenAPI operations plus generated Swagger/OpenAPI and Actuator routes; JSON/body, redirect `Location`, click-count, persisted lifecycle, and error-body assertions passed | [API test README](../evidence/api-tests/README.md), [endpoint inventory](../evidence/api-tests/endpoint-inventory.md), [test summary and screenshot index](../evidence/api-tests/test-summary.md), [raw transcript](../evidence/api-tests/api-smoke-console.txt); default profile/H2/loopback only. Direct response captures for the redirect `302` (curl.exe) and orchestration pending/execute/safe-stop/replan/approval operations were taken 2026-10-17 (40 API PNGs). | REQ-01, 02, 08, 11, 14, 16, 17, 24, 29 |
| Production auth focused tests | 7 passed, 0 failures/errors/skips (`ProductionAuthorizationBoundaryTest` 4; `ProductionSecurityIntegrationTest` 2; `ProductionFailClosedTest` 1); synthetic JWTs via MockMvc, not live IdP or live HTTP | Surefire reports; screenshot captures are indexed under [API test security evidence](../evidence/api-tests/test-summary.md#screenshot-index) | REQ-13, 29 |
| BDD | Explicit `mvn -B -Dtest=RunCucumberTest test` and final `mvn -B clean verify`: 16 scenarios, 76 steps passed; 0 failed; 0 skipped (3 features) | `target/cucumber-reports/cucumber.html`, `cucumber.json` (ignored, regenerated); [final report screenshot](../evidence/screenshots/05-bdd-cucumber-final-report.png) | REQ-01, 02, 08, 17 |
| OpenAPI contract and profile boundary | `OpenApiIntegrationTest`: 2 tests; generated business routes, request/response schemas, statuses, auth scheme, local Swagger UI; production tests verify both documentation routes are denied | Surefire XML for `OpenApiIntegrationTest` and `ProductionAuthorizationBoundaryTest` | REQ-24 |
| Symlink escape | 4 discovered, 3 executed, 1 skipped (Windows privilege) | `TEST-com.schwab.agentic.guardrails.ArtifactSandboxTest.xml` | REQ-12 (Partial) |

## CLI scenario transcripts (real headless runs)

Commands use `--spring.main.web-application-type=none`, the mock provider and a fresh in-memory H2 per process. New options: `--decision=APPROVE|REJECT --approver=<name> [--rationale=<text>] [--safe-stop-before-resume]`. They are always supplied by a human operator; nothing is approved automatically.

| Transcript | Scenario and mode | Observed result |
|---|---|---|
| [cli-01-greenfield.txt](../evidence/cli-01-greenfield.txt) | greenfield, sequential | `SUCCEEDED` |
| [cli-02-brownfield-pending-only.txt](../evidence/cli-02-brownfield-pending-only.txt) | brownfield, sequential | `AWAITING_APPROVAL` for `implementation` |
| [cli-03-brownfield-approve.txt](../evidence/cli-03-brownfield-approve.txt) | brownfield, approve | decision `APPROVED` by `cli-reviewer`, resumed, `SUCCEEDED` |
| [cli-04-brownfield-reject.txt](../evidence/cli-04-brownfield-reject.txt) | brownfield, reject | decision `REJECTED`, run `CANCELLED` |
| [cli-05-brownfield-approve-safe-stop-resume.txt](../evidence/cli-05-brownfield-approve-safe-stop-resume.txt) | brownfield, approve plus safe-stop | `SAFE_STOPPED`, then resumed, `SUCCEEDED` |
| [cli-06-ambiguous-pending-only.txt](../evidence/cli-06-ambiguous-pending-only.txt) | ambiguous, parallel | `AWAITING_APPROVAL` for `human-clarification` |
| [cli-07-ambiguous-approve.txt](../evidence/cli-07-ambiguous-approve.txt) | ambiguous, approve | `APPROVED`, resumed, `SUCCEEDED` |
| [cli-08-ambiguous-reject.txt](../evidence/cli-08-ambiguous-reject.txt) | ambiguous, reject | `REJECTED`, run `CANCELLED` |

The same lifecycles are asserted in `ScenarioLifecycleIntegrationTest`, including `verifyAudit` hash-chain validity. A CLI run ID is valid only for its own process, because H2 is in memory.

## Historical REST/security transcripts (previous HTTP Basic production profile)

These are genuine records from the previous app configuration, but they are **not evidence for the current OIDC/PostgreSQL profile** and must not be used to claim its live behavior.

| Transcript | Historical result |
|---|---|
| [rest-00-prod-fail-closed.txt](../evidence/rest-00-prod-fail-closed.txt) | Previous Basic-auth config failed startup without `APP_SECURITY_USERNAME`/`APP_SECURITY_PASSWORD` |
| [rest-01-prod-security-checks.txt](../evidence/rest-01-prod-security-checks.txt) | 19 of 19 checks matched against the prior Basic-auth configuration |

Production authentication is configured for OIDC JWT and PostgreSQL. Live issuer and PostgreSQL integration results are tracked as environment-specific release evidence in [the operational register](./OPERATIONS_AND_RELEASE_READINESS.md).

## Earlier general screenshots

The earlier general assessment captures remain under `evidence/screenshots/`. The expanded API-specific capture set is separately indexed at [evidence/api-tests/README.md](../evidence/api-tests/README.md) with per-image endpoint/result traceability in its [test summary](../evidence/api-tests/test-summary.md).

Captured on 2026-10-16 with headless Edge against the packaged JAR on loopback (default profile, port 8099, mock LLM, no authentication). They show only sample data. The embedded-browser attempt was discarded as unusable.

| Screenshot | Status |
|---|---|
| [swagger-ui.png](../evidence/screenshots/swagger-ui.png) | Captured |
| [url-stats-after-redirect.png](../evidence/screenshots/url-stats-after-redirect.png) (create, redirect, click count) | Captured |
| [approval-pending.png](../evidence/screenshots/approval-pending.png) and [run-awaiting-approval-state.png](../evidence/screenshots/run-awaiting-approval-state.png) (brownfield run `AWAITING_APPROVAL`) | Captured. They show the pending state only; approve/reject lifecycles are evidenced by text transcripts and tests |
| [bdd-cucumber-report.png](../evidence/screenshots/bdd-cucumber-report.png) | Captured from the generated HTML report |
| [01-maven-verify-final-summary.png](../evidence/screenshots/01-maven-verify-final-summary.png) | Captured from the genuine final Maven output; shows 149 tests, 5 skips, `BUILD SUCCESS`, and BDD step totals |
| [05-bdd-cucumber-final-report.png](../evidence/screenshots/05-bdd-cucumber-final-report.png) | Captured from the generated report after final `mvn clean verify`; 100% pass and all 3 feature files shown |
| [02-cli-greenfield-succeeded.png](../evidence/screenshots/02-cli-greenfield-succeeded.png), [03-cli-brownfield-approval-safe-stop-resume.png](../evidence/screenshots/03-cli-brownfield-approval-safe-stop-resume.png), [04-cli-ambiguous-approval-resume.png](../evidence/screenshots/04-cli-ambiguous-approval-resume.png), [06-cli-ambiguous-rejected.png](../evidence/screenshots/06-cli-ambiguous-rejected.png) | Captured in a browser from the genuine CLI output transcripts; show greenfield success, brownfield pending/approval/safe-stop/resume, ambiguous approval/resume, and ambiguous rejection. These are transcript screenshots, not terminal-window captures |
| Production-profile (OIDC/PostgreSQL) REST checks | Environment-specific evidence not recorded; requires a live IdP and PostgreSQL. Synthetic-JWT test-report screenshots are indexed separately under `evidence/api-tests/security/` and are not live HTTP evidence |

Precise Windows steps for remaining terminal/live items:

1. The final Maven summary, generated Cucumber report, and representative CLI transcript screenshots are captured. If terminal-window captures are additionally required, run `mvn -B clean verify` and the commands in [SCENARIOS.md](./SCENARIOS.md), then save genuine terminal captures with `Win+Shift+S`; label them separately from the existing transcript screenshots.
2. For current production auth evidence, use a controlled test issuer that signs a JWT with the configured audience and `agentic.operator` scope; configure a test PostgreSQL service and run requests through the TLS gateway. Do not use the old `rest-01-prod-security-checks.txt` HTTP Basic transcript as evidence for the current profile. Redact tokens before any screenshot.
3. Redact credentials and unrelated data before submission. Do not commit screenshots containing secrets.

## Local-only evidence

The generated `agent-workspace/` directories were preserved and remain ignored by `.gitignore`.
