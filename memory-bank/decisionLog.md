# Decision log

Current status: production deployment is **not ready/certified**; code-level hardening is not a substitute for live integration or operational evidence.

| Decision | Rationale | Alternatives | Consequences |
|---|---|---|---|
| Java 21, Spring Boot 3.5.16, Maven | User choice; current 3.x | Java 17 | Needs JDK 21 on PATH |
| Import JUnit BOM 5.13.0 for Cucumber 7.25.0 | Aligns Jupiter and Platform versions so the suite engine works | Older Cucumber | BOM must be kept in sync on upgrades |
| H2 for demo/tests; PostgreSQL + Flyway for prod profile | Keep assessment runnable without infrastructure while adding versioned production schema | Single DB mode | PostgreSQL is configured but not live-tested; backups/HA/recovery remain unverified |
| Persisted DAG with state machines and a run-row pessimistic lock | Auditable, safe concurrent updates | In-memory only; optimistic locking | Single-node only; no crash recovery |
| Mock LLM behind an interface | Runs without keys; deterministic | Real provider | No real model evidence |
| No DNS lookup for URL validation; literal-IP and host-suffix checks | Avoids SSRF through lookups and flaky tests | Resolve and check | DNS-rebinding not covered (documented gap) |
| `prod` profile uses OIDC JWT; require HTTPS issuer, audience, and `agentic.operator` scope | Standards-based authentication and API-level authorization; fail closed instead of creating a demo operator | HTTP Basic static account | Live issuer integration, claim mapping, key rotation and outage behavior remain unverified |
| TLS and rate limiting at trusted gateway | Keep transport and abuse controls at deployment boundary | Application-managed certificates/limiter | No gateway config or live gateway test; must be provided before exposure |
| PostgreSQL TLS defaults to `verify-full`; reject embedded connection-string credentials | Protect DB transport and keep secrets in separate environment/secret-manager settings | Plaintext or `prefer` mode | Requires correct DB hostname/CA for production; live connection was not tested |
| Default profile local-only (127.0.0.1, no auth) | Easy local demo | Always secured | Not for shared hosts |
| CLI approval needs explicit `--decision` and `--approver`; no auto-approve | Human-in-the-loop integrity | Auto-approve flag | Lifecycle in one process via `ScenarioLifecycleService` shared with tests |
| Safe-stop-before-resume is `requestSafeStop` then `execute` then `resume` | Exercises real safe-stop path | Direct state set | Documented in CLI notes |
| 403 tested via MockMvc JWT without operator scope | Avoid live IdP dependency in CI | Run a live IdP | No live token integration evidence |
| Discard unusable screenshot; document as not captured | No fabricated evidence | Keep clipped image | REQ-23 remains Not evidenced |
| Keep symlink test, allow skip when OS denies | Preserves the security assertion | Delete or weaken | Skip must be reported honestly |
| Memory Bank links to canonical docs | Avoid duplication and drift | Copy content | Update the dated facts each phase |
| Generate OpenAPI with springdoc-openapi 2.9.1 and keep docs routes local-only | Make the REST contract discoverable while preventing accidental production exposure; Spring Boot 3.5 uses the springdoc 2.x line | Hand-maintained spec; public production Swagger | Controllers/DTOs define schemas/statuses; generated docs are disabled and security-denied in `prod`; tests cover generated contract and profile boundary |
| Keep Copilot instructions in `.github/copilot-instructions.md` and Memory Bank details in `memory-bank/` | Give new sessions a short automatic workflow plus canonical project context without duplicating source facts | Put all guidance in README or rely on chat history | Copilot guidance requires source/diff inspection and fresh tests; Memory Bank must be updated after changes |
