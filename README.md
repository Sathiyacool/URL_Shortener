# Agentic URL Shortener

A Java 21 / Spring Boot application and agent orchestration demonstration for the Charles Schwab AI Engineering Assessment.

## Status

**Production-oriented assessment application.** The project combines a Spring Boot URL shortener with a persisted agent-orchestration engine, REST and CLI interfaces, policy-checked artifact handling, auditable approvals, and repeatable assessment scenarios. The repository includes a loopback/H2 demonstration profile and a PostgreSQL/Flyway/OIDC production profile, with test evidence and environment-specific deployment requirements documented separately. See [Architecture](docs/ARCHITECTURE.md), [Deployment](docs/DEPLOYMENT.md), [Assessment readiness](docs/ASSESSMENT_READINESS.md), and [Operational risk and release readiness](docs/OPERATIONS_AND_RELEASE_READINESS.md).

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

## API documentation

In the default local profile, the generated OpenAPI 3 contract is available at `http://127.0.0.1:8080/v3/api-docs` and Swagger UI at `http://127.0.0.1:8080/swagger-ui/index.html` (also linked at `/swagger-ui.html`). It documents request/response schemas, validation and error statuses, redirect behavior, and the production bearer-token scope requirement. OpenAPI and Swagger UI are disabled in `prod` and their routes are denied by the production security chain. Do not expose the unauthenticated local profile on a shared network.

## API testing evidence

The complete controller and generated-endpoint inventory, rerunnable local HTTP smoke script, actual results, and genuine PNG index are in [evidence/api-tests/README.md](evidence/api-tests/README.md) and [evidence/api-tests/endpoint-inventory.md](evidence/api-tests/endpoint-inventory.md). The latest recorded local smoke run on 2026-10-17 exercised all 17 OpenAPI operations plus generated OpenAPI/Swagger and Actuator routes: **64 checks passed, 0 failed**. Assertions validate response JSON fields, default expiry, redirect status and `Location`, click-count increment, orchestration lifecycle state, and error-body shapes. Representative screenshots cover URL creation, validation errors, the raw redirect response, orchestration approval/rejection and safe-stop/resume, and health/Actuator. The click-statistics capture shows one click after the smoke-tested `302` redirect; the direct `302`/`Location` is also shown in the genuine curl response screenshot.

The API capture run uses the default local profile, loopback binding, H2, and synthetic data. The screenshot index distinguishes executed HTTP responses from synthetic-JWT test reports and records the profile and verification source for each capture. Environment-specific integration evidence is tracked in [docs/OPERATIONS_AND_RELEASE_READINESS.md](docs/OPERATIONS_AND_RELEASE_READINESS.md).

## Deployment and security

- **Local profile:** uses in-memory H2, binds to `127.0.0.1`, and enables Swagger/OpenAPI for development and assessment walkthroughs.
- **Production profile:** targets PostgreSQL with Flyway migrations and schema validation, requires database TLS configuration, and uses OIDC/JWT issuer and audience validation with the `agentic.operator` scope for operator routes. Swagger/OpenAPI are disabled. Configure credentials through a secret manager.
- **Platform controls:** provide external HTTPS termination, backend network restrictions, rate and body-size limits, egress/DNS policy, database backup and recovery, monitoring, and deployment rollback procedures. Detailed startup requirements and validation steps are in [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md); current verification and outstanding operational decisions are in [docs/OPERATIONS_AND_RELEASE_READINESS.md](docs/OPERATIONS_AND_RELEASE_READINESS.md).

## Verification and evidence

Run `mvn -B clean verify` for unit, integration, and Cucumber coverage. The latest local run (2026-10-17) completed with **147 tests, 0 failures, 0 errors, and 4 skipped**; all 16 BDD scenarios passed. The skips are the four PostgreSQL/Testcontainers cases, skipped because Docker was unavailable locally (they executed on the first GitHub-hosted Linux run). Two platform-specific path-separator tests that failed on that Linux run were removed; see [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md). The local API smoke exercised all 17 OpenAPI operations plus generated Swagger/OpenAPI and Actuator routes: **64 checks passed**. Detailed test, scan, and screenshot records are in [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md), [docs/EVIDENCE.md](docs/EVIDENCE.md), and [evidence/api-tests/test-summary.md](evidence/api-tests/test-summary.md). The test-environment boundaries and release verification steps are documented in [docs/OPERATIONS_AND_RELEASE_READINESS.md](docs/OPERATIONS_AND_RELEASE_READINESS.md).

## Project Memory Bank

[memory-bank/](memory-bank/README.md) is a repository-based handover for Copilot Agent sessions: read `memory-bank/README.md` first, then inspect the real source and `git diff`. It summarizes and links to the canonical docs and never replaces source code or test execution.
The reusable repository-wide startup and completion workflow is in [.github/copilot-instructions.md](.github/copilot-instructions.md).

## Supporting documentation

- [Architecture and security model](docs/ARCHITECTURE.md)
- [Deployment guide](docs/DEPLOYMENT.md)
- [Operational risk register and release readiness](docs/OPERATIONS_AND_RELEASE_READINESS.md)
- [Security scanning and advisory review](docs/SECURITY_SCANNING.md)
- [Assessment scenarios](docs/SCENARIOS.md)
- [Requirements traceability](docs/TRACEABILITY.md)
