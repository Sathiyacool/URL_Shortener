# Agentic URL Shortener

A Java 21 / Spring Boot application and agent orchestration demonstration for the Charles Schwab AI Engineering Assessment.

## Status

**Assessment demonstration: implemented and verified. Production deployment: not certified and not yet production-ready.** P0–P10 assessment phases are complete; a production-hardening phase is in progress. The production profile now targets PostgreSQL with Flyway-managed schema and OIDC/JWT bearer-token authentication; the local profile remains loopback-only with in-memory H2. These are code/configuration changes, not evidence of a deployed or operationally ready service. See [docs/ASSESSMENT_READINESS.md](docs/ASSESSMENT_READINESS.md), [docs/PLAN.md](docs/PLAN.md), and [docs/RISKS_TRADEOFFS.md](docs/RISKS_TRADEOFFS.md) for implemented scope and remaining controls.

The URL shortener supports creation, redirects, configurable expiration, and click analytics. The orchestration core persists and executes dependency DAGs with parallel/sequential scheduling, retries, fallback, compensation, safe-stop/resume, high-impact approval gates, hash-chained audit history, reliability metrics, and dynamic replanning. The three assessment scenarios are available through REST and the Java CLI. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/SCENARIOS.md](docs/SCENARIOS.md) for design and reproducible workflows.

Seven agent roles use the `LlmProvider` boundary; the default deterministic `MockLlmProvider` needs no API key. Agents emit structured summaries and sandbox proposals; they do not execute commands or mutate application source. Fixed, registered Java checks are used for scenario validation; no model-generated command is run.

## Prerequisites

- JDK 21
- Apache Maven 3.9+

## Build and run

On Windows, set the toolchain paths in a PowerShell terminal if it still resolves an older system Java:

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\EclipseAdoptium\jdk-21.0.12.1+1"
$env:Path = "$env:LOCALAPPDATA\Programs\Apache\Maven\apache-maven-3.9.11\bin;$env:JAVA_HOME\bin;$env:Path"
mvn test
mvn spring-boot:run
```

The default profile uses an in-memory H2 database and binds to `127.0.0.1`. Health check: `http://127.0.0.1:8080/actuator/health`. Database migrations are managed by Flyway; Hibernate validates, rather than silently updates, the schema.

## URL API

Create a short URL:

```http
POST /api/urls
Content-Type: application/json

{"url":"https://example.com/articles","alias":"demo1","expiresAt":"2027-01-01T00:00:00Z"}
```

`alias` and `expiresAt` are optional. Aliases are 4–32 URL-safe characters; expiration must be in the future. Only absolute HTTP(S) destinations without embedded credentials are accepted. Loopback, private/link-local/shared IP literals, common local-only host suffixes, and ambiguous numeric IP forms are rejected without performing DNS lookups. A successful response is `201 Created` and includes the short URL in its body and `Location` header.
If `expiresAt` is omitted, the configured `app.default-expiration` is used (30 days by default). An explicit future expiration overrides that default.

- `GET /{code}` redirects with `302 Found` and records a click (timestamp, referrer, and user agent). Unknown codes return `404`; expired codes return `410` without recording a click.
- `GET /api/urls/{code}/stats` returns the total click count and up to 20 most recent click records. This endpoint is operator-protected in the production profile.

## Orchestration interfaces

- `GET /api/scenarios` lists the reproducible assessment scenarios.
- `POST /api/scenarios/{greenfield|brownfield|ambiguous}/run` creates and executes a scenario. Optional JSON body: `{"executionMode":"SEQUENTIAL"}` or `{"executionMode":"PARALLEL"}` (default).
- `POST /api/runs` creates a custom run. `GET /api/runs/{runId}` inspects state; `GET /api/runs/{runId}/audit` exports event and decision history.
- `GET /api/runs/{runId}/audit/verify` validates the per-run SHA-256 event chain and its stored tip anchor.
- `POST /api/runs/{runId}/execute` executes or continues a run; `POST /api/runs/{runId}/resume` resumes a safe-stopped or approval-paused run. Both accept optional `{"mode":"SEQUENTIAL"}` / `{"mode":"PARALLEL"}`.
- `POST /api/runs/{runId}/safe-stop` requests a cooperative stop; `POST /api/runs/{runId}/replan` submits a revised DAG and inputs.
- `GET /api/runs/{runId}/approvals/pending`, `GET /api/approvals/pending`, and `POST /api/approvals/{approvalId}` manage explicit human decisions.
- `GET /api/metrics/reliability` reports success rate, retry count, rollback frequency, MTTR, end-to-end latency percentiles, and approval wait percentiles.

The Java CLI runner invokes the same `ScenarioService` used by REST. For example, start the application and run the greenfield scenario sequentially:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--scenario=greenfield --mode=SEQUENTIAL"
```

For a headless CLI execution that closes after printing its result, include `--spring.main.web-application-type=none`. Add `--decision=APPROVE|REJECT --approver=<name>` (optionally `--rationale=<text>` and `--safe-stop-before-resume`) to drive the approval, rejection, safe-stop and resume lifecycle in the same process; see [docs/SCENARIOS.md](docs/SCENARIOS.md). The normal web-mode command leaves the server available so pending approvals can be handled and resumed. Scenario details and the matching REST examples are in [docs/SCENARIOS.md](docs/SCENARIOS.md).

Approval endpoints:

- `GET /api/approvals/pending` lists approval requests created by an executing HIGH-impact task.
- `POST /api/approvals/{approvalId}` accepts `{"decision":"APPROVE","approver":"reviewer","rationale":"Reviewed"}` or `REJECT`; duplicate decisions return `409 Conflict`.

Agents write only policy-checked artifacts under the configured sandbox. Brownfield checks call shortener Java services and repositories directly; arbitrary commands and arbitrary model-selected check IDs are not accepted.

## Security profiles

- **Default/local:** binds to loopback and allows local demo requests without authentication. Do not expose this profile on a shared or public network.
- **Production profile:** configure `SPRING_PROFILES_ACTIVE=prod`, `DATABASE_URL` (PostgreSQL JDBC URL without embedded credentials), `DATABASE_USERNAME`, `DATABASE_PASSWORD`, optional `DATABASE_SSL_MODE` (`verify-full` by default; only TLS modes accepted), `APP_OIDC_ISSUER_URI` (HTTPS issuer), and `APP_OIDC_AUDIENCE`. The application validates the OIDC issuer and audience and requires the `agentic.operator` scope for runs, approvals, scenarios, metrics, actuator (except health), and click analytics. Approval audit identity comes from the JWT subject, not a caller-supplied name. Use a secret manager; never place secret values in source control.
- **Deployment boundary:** terminate TLS at a trusted gateway, restrict direct access to the service, and configure gateway rate limits/abuse controls. The application does not implement TLS termination or rate limiting. URL destination validation does not resolve DNS, so enforce egress/DNS controls at the gateway/runtime.
- **Verification boundary:** production-profile authorization and Flyway schema validation are exercised with test JWTs and H2; no live OIDC issuer or PostgreSQL server was available for this verification. Do not treat profile configuration as production certification.

## Tests

Run `mvn clean verify` for the complete suite, including Cucumber feature tests. Last verified 2026-10-16 after production-profile hardening: **111 tests, 0 failures, 0 errors, 1 skipped; BDD 6 scenarios / 22 steps passed**. Coverage includes context startup, URL validation/expiry/analytics, OIDC authorization boundaries (401/403/fail-closed), API error contract, Flyway/H2 schema validation, execution concurrency, DAG/state transitions, retries/fallback/rollback/safe-stop, approval lifecycles, guardrails, replanning, scenarios, metrics, and audit integrity. Production OIDC issuer and PostgreSQL connectivity are **not** end-to-end tested. Requirement IDs (`REQ-nn`) are defined in [docs/TRACEABILITY.md](docs/TRACEABILITY.md). See also [docs/TEST_PLAN.md](docs/TEST_PLAN.md), [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md), [docs/ASSESSMENT_READINESS.md](docs/ASSESSMENT_READINESS.md), and [docs/EVIDENCE.md](docs/EVIDENCE.md). Screenshots have not been captured; text transcripts are in [evidence/](evidence/). The symlink escape test is preserved; on Windows it is skipped only when the OS refuses link creation.

## Project Memory Bank

[memory-bank/](memory-bank/README.md) is a repository-based handover for Copilot Agent sessions: read `memory-bank/README.md` first, then inspect the real source and `git diff`. It summarizes and links to the canonical docs and never replaces source code or test execution.

## Current limitations and risks

- The default database is in-memory H2: demo runs, approvals, and audit history are lost on restart. Production configuration targets PostgreSQL, but a live PostgreSQL deployment, backups, failover, restore drills, and operational tuning are not verified.
- Production authentication uses OIDC JWT validation and requires issuer/audience configuration plus `agentic.operator` scope. A live identity provider, key rotation, issuer outage behavior, and end-to-end token validation are not verified.
- TLS and request rate limiting are deployment gateway controls; there is no checked-in gateway configuration or load/abuse test.
- Crash recovery for interrupted orchestration runs, multi-instance scheduling/leases, and HA are not implemented or tested.
- Audit hash anchors remain in the same database and are not externally signed/anchored.
- No dependency vulnerability scan, penetration test, disaster recovery exercise, or production operational runbook has been completed.
- The default LLM is deterministic mock output; no production provider, model evaluation, or model-quality guarantee is included.
- Generated code is proposal text in the sandbox, not applied to the application. Build/test tasks are fixed registered Java checks, not arbitrary shell execution.
- Production authorization relies on OIDC JWTs and one required operator scope; role/tenant lifecycle, provider-specific claim mapping, credential operations, and live IdP verification remain deployment work.
- Host policy is intentionally DNS-independent. A hostname that resolves to a private address, DNS rebinding, and external open-redirect abuse require gateway/resolver controls or an allowlist policy.
- There is no in-process rate limiter; production deployments must enforce rate limits at a trusted gateway.
- Event hashes and the chain-tip anchor are stored in the same database. This detects ordinary edits, reordering, and truncation but is not a signed or externally anchored non-repudiation system.
