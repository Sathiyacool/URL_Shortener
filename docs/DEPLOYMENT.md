# Deployment guide

This guide describes the checked-in profiles and the deployment controls expected around them. The repository provides application configuration and verification evidence; it does not include a container image, infrastructure-as-code, or a deployed environment.

## Runtime profiles

### Local assessment profile

The default profile uses in-memory H2, binds to `127.0.0.1`, and enables Swagger/OpenAPI for local exploration. It is intended for development and assessment demonstrations. Do not bind it to a shared or public interface.

```powershell
mvn spring-boot:run
```

Health endpoint: `http://127.0.0.1:8080/actuator/health`.

### Production profile configuration

Activate `prod` and supply configuration through the deployment platform or secret manager:

| Variable | Purpose | Requirement/default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Select Spring profile | `prod` |
| `SERVER_ADDRESS` | Bind address | Defaults to `0.0.0.0`; restrict access at the network/gateway layer |
| `DATABASE_URL` | PostgreSQL JDBC URL | Required; do not embed credentials |
| `DATABASE_USERNAME` | Database account | Required; use a least-privilege account |
| `DATABASE_PASSWORD` | Database credential | Required; inject from a secret manager |
| `DATABASE_SSL_MODE` | PostgreSQL TLS verification | Defaults to `verify-full`; supported modes are validated by application configuration |
| `APP_OIDC_ISSUER_URI` | HTTPS OIDC issuer | Required |
| `APP_OIDC_AUDIENCE` | Required JWT audience | Required |

The production profile enables Flyway migrations and Hibernate schema validation, disables Swagger/OpenAPI, and fails startup when required database or OIDC configuration is absent or invalid. Application operator routes require a valid JWT with the configured issuer and audience and the `agentic.operator` scope. Approval attribution is derived from the JWT subject. URL creation and redirects remain public by design; protect them with edge controls appropriate to the service.

The application security configuration is detailed in [ARCHITECTURE.md](./ARCHITECTURE.md) and [SECURITY_SCANNING.md](./SECURITY_SCANNING.md). Do not store secret values in source, image layers, checked-in environment files, or logs.

## Required platform controls

- Terminate external HTTPS at a trusted gateway or ingress and restrict direct access to the application listener.
- Configure rate limits, request/body-size limits, abuse monitoring, and suitable quotas for public URL creation and redirect traffic.
- Enforce egress and DNS policy for redirect destinations. Application validation rejects certain literal local/private addresses but does not resolve hostnames or revalidate DNS at redirect time.
- Use PostgreSQL with TLS verification, least-privilege access, managed secrets, backups, restore drills, and a tested failover/recovery procedure.
- Keep the service to one active instance unless the deployment supplies and tests ownership/lease, recovery, and idempotency controls. Database row locks alone are not a complete multi-instance operations design.
- Define log retention/redaction, audit export and retention, alerting, incident response, rollback, and on-call ownership.
- Expose only approved health and metrics endpoints. The application exposes Actuator `health`, `info`, and `metrics`; readiness and liveness groups are not configured in the current profile.
- Do not rely on the in-process reliability metrics as a high-volume telemetry backend. Export/collect metrics in the platform monitoring system and define alert thresholds.

## Startup and release checks

1. Provision the identity provider, database, secret store, gateway, network policies, and monitoring before deploying the application.
2. Apply the `prod` profile variables through protected deployment configuration; verify that missing required variables cause startup failure.
3. Run migrations and validate schema compatibility against the selected PostgreSQL version.
4. Test OIDC discovery, JWKS, real issuer-issued claims, and authorization with the deployment's identity provider.
5. Verify TLS, network isolation, rate limits, destination/egress policy, health checks, telemetry, backup/restore, and rollback in a production-like environment.
6. Run the complete test and scan pipeline on the release candidate, review accepted advisories, and retain the artifact digest and reports.

See [OPERATIONS_AND_RELEASE_READINESS.md](./OPERATIONS_AND_RELEASE_READINESS.md) for the current evidence matrix, open operational risks, product decisions, and release acceptance checklist.

## Verification boundary

The repository's saved checks were run locally using H2 and synthetic JWT/local-key test fixtures. PostgreSQL/Testcontainers, live OIDC discovery/JWKS, GitHub Actions, Git-history secret scanning, and deployment-platform controls require external environments and are not represented as completed here. Refer to [TEST_RESULTS.md](./TEST_RESULTS.md) and [EVIDENCE.md](./EVIDENCE.md) for dated results.
