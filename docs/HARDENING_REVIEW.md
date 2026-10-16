# Hardening review: OIDC verification matrix and orchestration recovery

Reviewed 2026-10-16 against the source and tests at this commit's working tree. "Synthetic" means a test that fabricates tokens or contexts; it is **not** live integration.

## 1. Production OIDC/JWT: what is tested and how

Configuration: [SecurityConfiguration](../src/main/java/com/schwab/agentic/security/SecurityConfiguration.java) (`prod` profile). `NimbusJwtDecoder.withIssuerLocation(issuer)` uses OIDC discovery and the provider's JWKS; validators are `productionTokenValidator` (issuer + timestamps via `JwtValidators.createDefaultWithIssuer`, plus a required audience). Authority `SCOPE_agentic.operator` guards operator routes.

| Behavior | Evidence type | Test |
|---|---|---|
| Missing/blank issuer or audience fails startup | Real context startup | `ProductionFailClosedTest`, `SecurityConfigurationTest` |
| Non-HTTPS or malformed issuer rejected | Unit | `SecurityConfigurationTest` |
| Anonymous request to operator route → 401 | MockMvc, no token | `ProductionAuthorizationBoundaryTest` |
| Authenticated without `agentic.operator` scope → 403 | MockMvc, **synthetic JWT post-processor** | `ProductionAuthorizationBoundaryTest` |
| Operator scope allowed; approver identity taken from JWT subject | MockMvc, **synthetic JWT post-processor** | `ProductionSecurityIntegrationTest` |
| Swagger/OpenAPI denied in prod | MockMvc | `ProductionAuthorizationBoundaryTest` |
| Signature verified with the matching key; unknown key and tampered payload rejected | **Local simulation**: in-process RSA key, `NimbusJwtDecoder.withPublicKey`, production validator | `OidcLocalKeyValidationTest` |
| Expired token, wrong issuer, wrong audience rejected | **Local simulation**, same | `OidcLocalKeyValidationTest` |
| Audience validator logic | Unit | `SecurityConfigurationTest` |

The local-simulation tests use the production **validator composition** but not the production **decoder construction** (`withIssuerLocation`), because that performs network discovery.

**Requires a live test identity provider (not verified):**

- OIDC discovery document fetch and issuer match against `APP_OIDC_ISSUER_URI`.
- JWKS retrieval over TLS, caching, and key rotation (new `kid`, retired keys).
- Behavior during an IdP outage (cached keys versus failures).
- Real token claim shapes: the `scope`/`scp` claim form and the `aud` claim format of the actual provider, and whether `agentic.operator` can be granted to real users.
- Clock-skew tolerance against a real issuer (Spring's default 60 s skew is in effect and untested end to end).
- Token revocation/session lifetime policy (none is implemented in the application).

A WireMock-based discovery/JWKS stub could exercise decoder construction, rotation and outage handling locally. It would still be a simulation and would need to be labeled so.

## 2. Orchestration recovery, duplicate execution, approval expiry, idempotency, concurrency

| Topic | Current behavior (source) | Tested | Gap / decision |
|---|---|---|---|
| Duplicate/concurrent execution of one run | `findByIdForUpdate` pessimistic lock plus `@Version`; an already `RUNNING` or finished run is rejected with `IllegalStateException` | `ExecutionEngineIntegrationTest.rejectsConcurrentExecutionOfAnAlreadyRunningRun`; `AbstractPersistenceContract.concurrentExecutionOfOneRunIsAdmittedExactlyOnce` (H2 executed; PostgreSQL skipped) | Single-database protection only. No distributed lease/heartbeat. |
| Double approval decision | Approval row lock; second decision throws `ApprovalAlreadyDecidedException` → 409 | `ApprovalApiIntegrationTest`; BDD "A decided approval cannot be decided a second time" | None. |
| Retry bound | `RetryPolicy` max attempts 3, exponential delay, no retry after interrupt/stop | `ExecutionEngineIntegrationTest`; BDD retry exhaustion | None. |
| Safe-stop / resume | Cooperative flag; consumed by `execute`, cleared by `resume` | JUnit lifecycle tests; BDD | A REST caller must resume to clear the flag before replanning (replan returns 409 while the flag is set). Documented behavior, left unchanged. |
| Interrupted process (crash while `RUNNING`/`RETRYING`) | **Not implemented.** No startup reconciliation. A run left `RUNNING` is rejected by `execute` and `resume` ("not executable"/"Only safe-stopped or approval-paused runs"), so it is stuck until manual database repair. | No | Real gap. |
| Approval expiry | **Not implemented.** Pending approvals wait indefinitely. | No | Real gap. |
| Idempotency | No idempotency keys on `POST /api/urls` or `POST /api/runs`; repeating a call creates a new resource. Aliases are unique, so a duplicate alias conflicts. | Alias conflict covered in shortener tests | Gap for retried clients. |

**Decision: no recovery or expiry code was added in this pass.** Crash recovery needs a design decision that the existing requirements do not make (resume versus fail versus compensate; lease timeouts; how to treat side effects of a half-run task) and cannot be verified without fault-injection against PostgreSQL. A reconciler that guessed wrongly could silently re-run a high-impact task that was already approved. Approval expiry needs a product decision (expiry period, auto-reject versus re-request). Both stay listed as production gates in [ASSESSMENT_READINESS.md](./ASSESSMENT_READINESS.md) with the proposed design below.

Proposed designs (not implemented):

- Startup reconciler: find runs in `RUNNING` with no heartbeat newer than a lease timeout, mark their in-flight tasks `FAILED` with reason `INTERRUPTED`, record an audit event, and move the run to `SAFE_STOPPED` so a human can resume or replan. Verify with a PostgreSQL fault-injection test (kill the process mid-run).
- Approval expiry: add `expiresAt` to approval requests, a scheduled job that moves expired `PENDING` approvals to `EXPIRED` and the run to `SAFE_STOPPED`, audit-logged.
