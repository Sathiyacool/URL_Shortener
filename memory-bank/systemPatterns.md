# System patterns

Canonical detail: [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md). Base package `com.schwab.agentic`.

Production status: not certified. Profile code exists, but live PostgreSQL/OIDC and gateway controls are not verified.

## Modules (under `src/main/java/com/schwab/agentic`)

| Package | Responsibility |
|---|---|
| `shortener` | URL create/redirect/expiry/click analytics (`ShortUrlService`, `ShortUrlController`, `RedirectController`) |
| `orchestration` | DAG model and validation, task/run state machines, execution engine, retries, fallback, compensation, safe-stop, replanning, persistence |
| `orchestration.approval` | `ApprovalService`, `ApprovalController`: human approval checkpoints |
| `orchestration.api` | `OrchestrationRunService`, `RunController` |
| `scenarios` | `ScenarioService`, `ScenarioController`, `ScenarioCliRunner`, `ScenarioLifecycleService` |
| `metrics` | Reliability metrics: success rate, retries, rollback frequency, MTTR, latency |
| `security` | `SecurityConfiguration` (loopback demo profile, OIDC/JWT `prod` chain) |
| `api` | OpenAPI metadata and bearer-token security scheme |
| persistence config | H2 default/test; production PostgreSQL with Flyway and Hibernate schema validation |
| agent/LLM, artifacts, policy | Provider interface plus deterministic mock, sandboxed artifact store and versioning, policy engine/guardrails |

## DAG orchestration

- Explicit persisted DAG, validated by `DagValidator` (cycles rejected).
- Run and task state machines (`RunStateMachine`, `TaskStateMachine`) reject illegal transitions.
- Independent tasks run in parallel (SEQUENTIAL or PARALLEL mode); dependents wait for all upstream tasks.
- A pessimistic lock on the run row serializes state changes.
- Retries are bounded, then fallback, then reverse-order compensation.
- Safe-stop is cooperative. `resume` requires `SAFE_STOPPED` or `AWAITING_APPROVAL` and clears the stop flag.
- Dynamic replan invalidates only the affected downstream branch and records lineage.
- Audit events are hash-chained; `/audit/verify` checks the chain. The anchor lives in the same database (a limitation).

## REST endpoints (from controller source)

- Public: `POST /api/urls`, `GET /{code}`, actuator health.
- `/api/urls/{code}/stats`.
- `/api/runs`: `POST`, `GET /{id}`, `/audit`, `/audit/verify`, `/approvals/pending`, `POST /execute`, `/resume`, `/safe-stop`, `/replan`.
- `/api/approvals`: `GET /pending`, `POST /{approvalId}`.
- `/api/scenarios`: `GET`, `POST /{id}/run`.
- `GET /api/metrics/reliability`.

## Security model

- Default profile: binds `127.0.0.1`, unauthenticated (local demo only).
- `prod` profile: OIDC JWT; configured HTTPS issuer (`APP_OIDC_ISSUER_URI`), audience (`APP_OIDC_AUDIENCE`), signature/time validation and required `agentic.operator` scope. Startup fails closed if issuer/audience are missing/invalid.
- PostgreSQL values: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`; DB TLS `DATABASE_SSL_MODE` defaults to `verify-full`; embedded URL credentials and disabled TLS are rejected. Migrations at `src/main/resources/db/migration`; `ddl-auto=validate`.
- Protected in `prod`: approvals, runs, scenarios, metrics, stats and actuator (except health). Approver identity comes from JWT subject.
- OpenAPI/Swagger routes are available for local development, disabled by `application-prod.yml`, and denied by the production security chain.
- Not verified: live database/IdP, TLS gateway, rate limiting, DNS-rebinding/egress policy, backup/restore, crash recovery, multi-instance leases.

## Approval gates

High-impact tasks (brownfield `implementation`, ambiguous `human-clarification`) are blocked until an approval is `APPROVED`. Reject cancels the run. The CLI never auto-approves: `ScenarioLifecycleService` requires `--decision`, `--approver` and a rationale.

## Design patterns

State machine, DAG scheduler, strategy (LLM provider interface and mock), compensation (saga-style rollback), policy engine, hash-chained audit log, shared service layer used by both REST and CLI.
