# Product context

## URL shortener use cases (implemented, tested; REQ-01 to REQ-03)

- `POST /api/urls` creates a short URL: http(s) only, optional custom alias, optional `expiresAt`; a configured default expiration of 30 days applies when none is supplied (brownfield enhancement).
- `GET /{code}` returns `302`; `404` unknown; `410` expired (no click recorded for expired).
- `GET /api/urls/{code}/stats` returns total clicks and recent clicks (operator-protected in `prod`).
- Rejected destinations: non-http(s), embedded credentials, local/private literal IPs, local-only host suffixes, ambiguous numeric IP forms, all without DNS lookup. Alias conflict returns `409`.

## Orchestration scenarios (REQ-17)

| Scenario | Behavior (verified) |
|---|---|
| Greenfield | Runs to `SUCCEEDED` with no high-impact approval |
| Brownfield | Establishes a baseline (`baseline-validation` succeeds), then pauses `AWAITING_APPROVAL` at task `implementation` (default-expiration enhancement). Approve gives resume, expiration regression and `SUCCEEDED`; reject gives `CANCELLED`. Code proposals are sandbox artifacts, not applied to source |
| Ambiguous | Pauses `AWAITING_APPROVAL` at `human-clarification`. Approve gives `SUCCEEDED`; reject gives `CANCELLED`. The human rationale becomes downstream context |

Details and reproducible commands: [docs/SCENARIOS.md](../docs/SCENARIOS.md). Real CLI outputs: `evidence/cli-*.txt`.

## Expected behavior of the engine

High-impact tasks never dispatch before an approval is `APPROVED`; rejection cancels the run; retries are bounded; failures trigger fallback and then reverse-order compensation; safe-stop halts cooperatively and a run can be resumed; a requirement change invalidates only the affected downstream branch; every transition is an audited, hash-chained event.

## Production hardening boundary

The `prod` profile is configured for PostgreSQL and OIDC JWT with issuer, audience, and operator-scope checks. The default profile uses in-memory H2 for local demos. Live integration results and deployment controls—including identity-provider validation, gateway TLS/rate limits, managed secrets, database operations, and recovery—are tracked in `docs/OPERATIONS_AND_RELEASE_READINESS.md`.

## Not product scope

No UI or real LLM provider. No provider-specific identity lifecycle/claim mapping, TLS termination, or rate limiter is implemented by the application.
