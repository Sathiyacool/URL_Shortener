# API testing evidence

This folder contains a route inventory, repeatable local HTTP test, original browser captures, and a summary of evidence scope and provenance.

## Start and rerun

Prerequisite: the packaged JAR exists (build it with `mvn clean package`). In one PowerShell window, start the local default-profile application:

```powershell
$env:JAVA_HOME="$env:LOCALAPPDATA\Programs\EclipseAdoptium\jdk-21.0.12.1+1"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -jar target\agentic-url-shortener-0.1.0-SNAPSHOT.jar `
  --server.port=8099 `
  --app.public-base-url=http://127.0.0.1:8099
```

In a second PowerShell window:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\api-evidence-smoke.ps1
```

The script uses only loopback and synthetic URL data, creates a random alias, allows a short expiry to elapse, and exercises URL creation/redirect/statistics plus scenario, run, approval, safe-stop, replan, audit, metrics, OpenAPI, and Actuator endpoints. It asserts response JSON fields, the configured 30-day default expiry, HTTP 302 and the expected `Location`, the click-count increment, persisted orchestration states, and accepted error-response shapes. Failed assertions increment the failure total and cause a nonzero exit. The latest 64/64 result is [`api-smoke-console.txt`](./api-smoke-console.txt).

Swagger UI is at `http://127.0.0.1:8099/swagger-ui/index.html`; the generated OpenAPI document is at `/v3/api-docs`. The captured URL-create, invalid-input, and duplicate-alias requests were executed in Swagger UI. Direct GET response screenshots were captured from the running browser. Screenshots under `security/` are actual Surefire text reports, explicitly not live HTTP or live IdP evidence.

## Contents

- [`endpoint-inventory.md`](./endpoint-inventory.md): all 17 controller operations, generated OpenAPI routes, Actuator routes, inputs, status cases, authentication boundary and evidence references.
- [`test-summary.md`](./test-summary.md): actual API and focused security results, screenshot traceability, and blocked production checks.
- `swagger/`, `url-shortener/`, `orchestration/`, `security/`, `errors-validation/`, `health-actuator/`: original PNG captures organized by purpose.

## Verification boundary

The screenshots and 64-check API smoke run used the local/default profile with in-memory H2, no authentication, and loopback HTTP. The screenshots do not establish deployed or production behavior. Direct request/response captures (2026-10-17) cover the raw redirect `302` (real `curl.exe` output, not Swagger) and per-run pending approvals, execute, safe-stop, replan success/conflict and approval approve/reject (Swagger executed panels); see [`test-summary.md`](./test-summary.md). Production authentication tests use synthetic MockMvc JWTs; a live OIDC issuer and PostgreSQL service were unavailable. See [`../../docs/HARDENING_REVIEW.md`](../../docs/HARDENING_REVIEW.md) and [`../../docs/ASSESSMENT_READINESS.md`](../../docs/ASSESSMENT_READINESS.md).
