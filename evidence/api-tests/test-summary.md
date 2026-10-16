# API test and screenshot summary

**Execution date:** 2026-10-17
**Application:** packaged `agentic-url-shortener-0.1.0-SNAPSHOT.jar`, Java 21, local/default profile, H2, loopback `127.0.0.1:8099`, deterministic mock LLM. The active profile was not `prod`.

## Actual execution results

| Check | Actual result | Evidence scope |
|---|---|---|
| Local HTTP endpoint smoke | **64 checks passed, 0 failed**. The live loopback checks assert response status, JSON fields, default expiry, redirect `Location`, click increment, orchestration state and error-body shape. | [`api-smoke-console.txt`](./api-smoke-console.txt); rerunnable with [`scripts/api-evidence-smoke.ps1`](../../scripts/api-evidence-smoke.ps1). This does not replace the full JUnit suite. |
| URL journey | `POST /api/urls` returned `201`; `GET /{code}` returned `302` with the expected `Location` and returned `404` for an unknown code; `GET /api/urls/{code}/stats` returned `200` with exactly one click; expired redirect returned `410`. | Smoke transcript; redirect and unknown-code statuses appear in [`03-redirect-result-302-smoke.png`](./url-shortener/03-redirect-result-302-smoke.png), and the stats capture shows `totalClicks: 1`. |
| Orchestration API lifecycle | Run creation, state, audit read/hash verification, pending approvals, grant/resume, rejection/terminal behavior, safe-stop/resume, replanning and reliability metrics exercised. Rejected-run resume and repeated approval return `409`; invalid inputs and missing IDs return their expected `400`/`404`. | Smoke transcript; representative actual browser response screenshots below; route-by-route mapping in [`endpoint-inventory.md`](./endpoint-inventory.md). |
| Production security tests | **7 tests passed, 0 failed, 0 errors, 0 skipped**: `ProductionAuthorizationBoundaryTest` (4), `ProductionSecurityIntegrationTest` (2), `ProductionFailClosedTest` (1). | Synthetic JWTs via MockMvc test support. Not HTTP against a live `prod` process or a live identity provider. Screenshots in `security/` are Surefire text-report captures, not security response screenshots. |
| Full Maven verification | Final `mvn -B clean verify` rerun on 2026-10-17 00:59:33 +05:30: **149 tests, 144 passed, 0 failures, 0 errors, 5 skipped; `BUILD SUCCESS` (exit 0)**. | Latest raw log: [`../../evidence/maven-verify-final.txt`](../../evidence/maven-verify-final.txt). The genuine summary screenshot is from the earlier 2026-10-16 successful run with the same counts; see [`../../docs/TEST_RESULTS.md`](../../docs/TEST_RESULTS.md). |
| BDD | Explicit `mvn -B -Dtest=RunCucumberTest test` and final full verification: **3 feature files, 16 scenarios and 76 steps passed; 0 failed and 0 skipped**. | Current Cucumber JSON/HTML report, [final HTML report screenshot](../../evidence/screenshots/05-bdd-cucumber-final-report.png), and [`../../docs/TEST_RESULTS.md`](../../docs/TEST_RESULTS.md). |

### API behavior exercised

The smoke script covers every one of the 17 application operations listed in the inventory, plus generated Swagger/OpenAPI and Actuator endpoints. It checks URL creation success and validation/conflict cases, redirect/unknown-code/click/expiry behavior, scenario listing and run creation, status/audit/audit verification, both approval list routes, execution, grant/reject decisions, safe-stop/resume, replan success/conflict, and reliability metrics. It also probes missing IDs, unknown scenarios/approvals, blank approvers, duplicate decisions, invalid state transitions, OpenAPI/Swagger and Actuator exposure.

The local profile is intentionally unauthenticated. Production `401`/`403` boundaries are verified only by the synthetic-JWT MockMvc tests above; no screenshot claims to show a live unauthorized or forbidden response. Production-profile HTTP and live OIDC discovery/JWKS/claim behavior require a test identity provider; PostgreSQL/Testcontainers verification requires Docker/PostgreSQL. Track these external validation items in [the operational risk register](../../docs/OPERATIONS_AND_RELEASE_READINESS.md).

## Screenshot index

All screenshots listed below are original PNG files captured on 2026-10-16 or 2026-10-17 from the real local application/browser, actual local HTTP output, or a genuine Surefire text report. Browser captures used the local/default profile and synthetic test data; report captures are separately labeled. `Captured` means the PNG exists in the repository. The Swagger overview, URL creation response, redirect smoke result, approved/resumed run state, and authorization test report were visually reviewed; all other listed captures were checked for file presence and are mapped to their actual smoke/report evidence. No screenshot represents a live production authentication check.

For each local HTTP screenshot without a separate JUnit class named in its row, the related check is the corresponding labeled case in [`scripts/api-evidence-smoke.ps1`](../../scripts/api-evidence-smoke.ps1) and [`api-smoke-console.txt`](./api-smoke-console.txt). JUnit classes are named where they are the primary verification source.

| Screenshot | Endpoint / method and scenario | Expected → actual | Capture source, profile, result |
|---|---|---|---|
| [`swagger/01-swagger-api-overview.png`](./swagger/01-swagger-api-overview.png) | `GET /swagger-ui/index.html`, `GET /v3/api-docs`; overview of published local operations | `200` → `200` | Real Edge browser; default profile; captured, visually reviewed. `OpenApiIntegrationTest`. |
| [`url-shortener/02-url-create-success-swagger.png`](./url-shortener/02-url-create-success-swagger.png) | `POST /api/urls`; synthetic alias `apiEv2511`, example destination | `201` → `201`, generated code and short URL shown | Swagger UI executed request; default profile; captured, visually reviewed. `ShortUrlApiIntegrationTest`. |
| [`url-shortener/03-redirect-result-302-smoke.png`](./url-shortener/03-redirect-result-302-smoke.png) | `GET /{code}`; redirect, unknown code, and stats after API smoke created a synthetic short code | `302` → `302`, unknown code `404` → `404`; adjacent stats request `200` | Genuine browser capture of the saved API smoke test output; default-profile HTTP check via `scripts/api-evidence-smoke.ps1`; visually reviewed. The `Location` header value is recorded in `api-smoke-console.txt`. |
| [`errors-validation/03-url-invalid-destination-400.png`](./errors-validation/03-url-invalid-destination-400.png) | `POST /api/urls`; invalid destination scheme | `400` → `400` | Swagger UI response; default profile; captured. `ShortUrlApiIntegrationTest`. |
| [`errors-validation/04-url-duplicate-alias-409.png`](./errors-validation/04-url-duplicate-alias-409.png) | `POST /api/urls`; duplicate synthetic alias | `409` → `409` | Swagger UI response; default profile; captured. `ShortUrlApiIntegrationTest`. |
| [`errors-validation/05-url-missing-required-field-400.png`](./errors-validation/05-url-missing-required-field-400.png) | `POST /api/urls`; missing required URL | `400` → `400` | Swagger UI response; default profile; captured. `ShortUrlApiIntegrationTest`. |
| [`url-shortener/05-click-analytics-stats-200.png`](./url-shortener/05-click-analytics-stats-200.png) | `GET /api/urls/{code}/stats`; follows the successful short link | `200`, one click → `200`, `totalClicks: 1` | Real browser response from local app; default profile; captured. Redirect `302` and `Location` are also in the smoke transcript. |
| [`errors-validation/06-unknown-url-stats-404.png`](./errors-validation/06-unknown-url-stats-404.png) | `GET /api/urls/{code}/stats`; unknown code | `404` → `404` | Real browser response; default profile; captured. |
| [`errors-validation/07-expired-url-410.png`](./errors-validation/07-expired-url-410.png) | `GET /{code}`; synthetic expired link | `410` → `410` | Real browser response; default profile; captured. |
| [`errors-validation/06-unknown-run-404.png`](./errors-validation/06-unknown-run-404.png) | `GET /api/runs/{runId}`; unknown run ID | `404` → `404` | Real browser response; default profile; captured. |
| [`orchestration/08-scenario-catalog-200.png`](./orchestration/08-scenario-catalog-200.png) | `GET /api/scenarios`; three reproducible scenarios | `200` → `200` | Real browser response; default profile; captured. |
| [`orchestration/06-run-create-201.png`](./orchestration/06-run-create-201.png) | `POST /api/runs`; custom DAG run creation | `201` → `201` | Real browser response; default profile; captured. |
| [`orchestration/07-greenfield-run-state-200.png`](./orchestration/07-greenfield-run-state-200.png) | `POST /api/scenarios/{scenarioId}/run`, then `GET /api/runs/{runId}`; greenfield DAG | `200` → `200`, run succeeded | Real browser response; default profile; captured. |
| [`orchestration/12-audit-history-200.png`](./orchestration/12-audit-history-200.png) | `GET /api/runs/{runId}/audit`; synthetic run history | `200` → `200` | Real browser response; default profile; captured. |
| [`orchestration/08-audit-chain-verify-200.png`](./orchestration/08-audit-chain-verify-200.png) | `GET /api/runs/{runId}/audit/verify`; hash chain | `200`, valid chain → `200`, `valid: true` | Real browser response; default profile; captured. |
| [`orchestration/09-pending-approvals-200.png`](./orchestration/09-pending-approvals-200.png) | `GET /api/approvals/pending`; pending brownfield gate | `200` and pending decision → `200` and pending decision | Real browser response; default profile; captured. |
| [`orchestration/10-run-awaiting-approval-200.png`](./orchestration/10-run-awaiting-approval-200.png) | `GET /api/runs/{runId}` and per-run pending state | `AWAITING_APPROVAL` → `AWAITING_APPROVAL` | Real browser response; default profile; captured. |
| [`orchestration/11-approved-resumed-run-200.png`](./orchestration/11-approved-resumed-run-200.png) | `POST /api/approvals/{approvalId}`, `POST /api/runs/{runId}/resume`; brownfield | approval/resume succeeds → run `SUCCEEDED` | Real browser response; default profile; captured, visually reviewed. |
| [`orchestration/12-rejected-run-cancelled-200.png`](./orchestration/12-rejected-run-cancelled-200.png) | `POST /api/approvals/{approvalId}`; rejected brownfield run | decision accepted → run `CANCELLED` | Real browser response; default profile; captured. |
| [`orchestration/20-approved-decision-audit-200.png`](./orchestration/20-approved-decision-audit-200.png) | `GET /api/runs/{runId}/audit`; approved decision lineage | approval actor/rationale recorded → present in audit | Real browser response; default profile; captured. |
| [`orchestration/21-rejected-decision-audit-200.png`](./orchestration/21-rejected-decision-audit-200.png) | `GET /api/runs/{runId}/audit`; rejection decision lineage | rejection recorded → present in audit | Real browser response; default profile; captured. |
| [`orchestration/18-safe-stop-paused-200.png`](./orchestration/18-safe-stop-paused-200.png) | `POST /api/runs/{runId}/safe-stop`, then execution/state | cooperative stop → run paused | Real browser response; default profile; captured. |
| [`orchestration/19-safe-stop-resumed-200.png`](./orchestration/19-safe-stop-resumed-200.png) | `POST /api/runs/{runId}/resume`; safe-stop lifecycle | resume succeeds → execution continues | Real browser response; default profile; captured. |
| [`orchestration/13-reliability-metrics-200.png`](./orchestration/13-reliability-metrics-200.png) | `GET /api/metrics/reliability` | `200` → `200`, metric response | Real browser response; default profile; captured. |
| [`health-actuator/13-actuator-discovery-200.png`](./health-actuator/13-actuator-discovery-200.png) | `GET /actuator`; permitted local links | `200` → `200` | Real browser response; default profile; captured. |
| [`health-actuator/14-health-200.png`](./health-actuator/14-health-200.png) | `GET /actuator/health`; public health | `200` / `UP` → `200` / `UP` | Real browser response; default profile; captured. |
| [`health-actuator/15-actuator-metrics-200.png`](./health-actuator/15-actuator-metrics-200.png) | `GET /actuator/metrics` | `200` → `200` | Real browser response; default profile; captured. |
| [`health-actuator/17-info-200.png`](./health-actuator/17-info-200.png) | `GET /actuator/info` | `200` → `200` | Real browser response; default profile; captured. |
| [`health-actuator/16-env-not-exposed-404.png`](./health-actuator/16-env-not-exposed-404.png) | `GET /actuator/env`; sensitive endpoint not exposed | `404` → `404` | Real browser response; default profile; captured. |
| [`security/13-mockmvc-auth-boundary-4-passed.png`](./security/13-mockmvc-auth-boundary-4-passed.png) | Protected route auth boundaries (controller paths in `ProductionAuthorizationBoundaryTest`) | 4 tests, 0 failures → 4 passed | Genuine Surefire report screenshot; synthetic JWT/MockMvc, not a live HTTP capture; visually reviewed. |
| [`security/14-mockmvc-protected-routes-2-passed.png`](./security/14-mockmvc-protected-routes-2-passed.png) | Protected-route integration checks | 2 tests, 0 failures → 2 passed | Genuine Surefire report screenshot; synthetic JWT/MockMvc, not a live HTTP capture. |
| [`security/15-production-fail-closed-1-passed.png`](./security/15-production-fail-closed-1-passed.png) | Production security configuration with missing required values | 1 test, 0 failures → 1 passed | Genuine Surefire report screenshot; configuration test, not a live IdP response. |
| [`url-shortener/04-redirect-curl-response-302.png`](./url-shortener/04-redirect-curl-response-302.png) | `GET /{code}` raw redirect | `302` + `Location` header → `302`, `Location: https://example.com/api-evidence` | Real `curl.exe` HTTP client output (rendered in a browser for capture); default profile; 2026-10-17; passed. Smoke checks; `ShortUrlApiIntegrationTest`. |
| [`orchestration/22-run-pending-approvals-response-200.png`](./orchestration/22-run-pending-approvals-response-200.png) | `GET /api/runs/{runId}/approvals/pending` | `200` → `200` | Swagger UI executed request/response; default profile; 2026-10-17; passed. Synthetic brownfield run. |
| [`orchestration/23-execute-response-200.png`](./orchestration/23-execute-response-200.png) | `POST /api/runs/{runId}/execute` (SEQUENTIAL) | `200`, run SUCCEEDED → `200`, SUCCEEDED | Swagger UI executed request/response; default profile; 2026-10-17; passed. |
| [`orchestration/24-safe-stop-response-200.png`](./orchestration/24-safe-stop-response-200.png) | `POST /api/runs/{runId}/safe-stop` | `200` → `200` | Swagger UI executed request/response; default profile; 2026-10-17; passed. Run was CREATED and stayed CREATED. |
| [`orchestration/25-replan-success-200.png`](./orchestration/25-replan-success-200.png) | `POST /api/runs/{runId}/replan` on SUCCEEDED run | `200` → `200` (status CREATED) | Swagger UI executed request/response; default profile; 2026-10-17; passed. |
| [`orchestration/26-replan-conflict-409.png`](./orchestration/26-replan-conflict-409.png) | `POST /api/runs/{runId}/replan` on AWAITING_APPROVAL run | `409` → `409` "Run cannot be replanned from state AWAITING_APPROVAL" | Swagger UI executed request/response; default profile; 2026-10-17; passed. |
| [`orchestration/27-approval-decision-response-200.png`](./orchestration/27-approval-decision-response-200.png) | `POST /api/approvals/{approvalId}` APPROVE | `200`, APPROVED → `200`, APPROVED | Swagger UI executed request/response; default profile; 2026-10-17; passed. |
| [`orchestration/28-approval-reject-response-200.png`](./orchestration/28-approval-reject-response-200.png) | `POST /api/approvals/{approvalId}` REJECT | `200`, REJECTED → `200`, REJECTED | Swagger UI executed request/response; default profile; 2026-10-17; passed. |

## Additional environment-specific evidence

- The raw redirect `302` is captured with a real `curl.exe -i --max-redirs 0` response (Swagger/browsers follow redirects), rather than a Swagger panel. Direct Swagger executed-request/response panels cover per-run pending approvals, execute, safe-stop, replan (success and conflict), and approval decisions (approve and reject). All indexed screenshots are original captures.
- Production `401`/`403` HTTP captures and live OIDC/JWKS validation require a configured test identity provider. MockMvc synthetic JWT tests exercise the application security boundaries without representing a live identity-provider integration.
- PostgreSQL-specific results require Docker/PostgreSQL; Testcontainers tests were skipped because Docker was unavailable during the recorded run.
- Local smoke and JUnit results are scoped to their recorded profiles and environments; they are not substitutes for external integration evidence.

### Windows capture status

The previously missing direct request/response captures were taken on 2026-10-17 (see index rows 04 and 22-28). Nothing in the local/default profile remains uncaptured. The remaining items below require external infrastructure.

Production HTTP screenshots require a controlled test PostgreSQL instance and live test OIDC issuer. Once provisioned, set `SPRING_PROFILES_ACTIVE=prod`, the documented database settings, `APP_OIDC_ISSUER_URI`, and `APP_OIDC_AUDIENCE` in a protected PowerShell process environment, start the application, and use actual issuer-issued test tokens:

```powershell
curl.exe -i http://127.0.0.1:8099/api/scenarios
curl.exe -i -H "Authorization: Bearer $env:TEST_TOKEN_WITHOUT_OPERATOR_SCOPE" http://127.0.0.1:8099/api/scenarios
curl.exe -i -H "Authorization: Bearer $env:TEST_OPERATOR_TOKEN" http://127.0.0.1:8099/api/scenarios
```

Expected statuses are `401`, `403`, and `200` respectively. Capture the request/response terminal using `Win+Shift+S`; keep token values out of visible commands/output and mask any credential-bearing headers before storing the PNG. Save only genuine captures under `security/` and update this table after observing the actual result.
