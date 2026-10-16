# Progress

Requirement IDs `REQ-01` to `REQ-29` and per-requirement status are canonical in [docs/TRACEABILITY.md](../docs/TRACEABILITY.md). Do not redefine them here. Vocabulary: Implemented, Tested, Partial, Not implemented / Not evidenced.

## Phases (see [docs/PLAN.md](../docs/PLAN.md))

P0-P14 complete and published (`cbbbbd2`, `d7ecc18`). P15 hardening and P16 API evidence are complete locally and **uncommitted**. Keep descriptions of deployment maturity aligned with the verified evidence and the operational release register.

The 2026-10-17 final verification pass strengthened `scripts/api-evidence-smoke.ps1` to assert response JSON, default expiry, 302/Location, click-count increment, persisted orchestration states, and error-body shapes; the actual local HTTP result is 64/64 checks passed. The endpoint inventory covers all 17 OpenAPI routes plus generated Swagger/OpenAPI and Actuator routes. All 40 retained API PNGs are individually indexed; direct captures show the raw redirect `302` (curl.exe, not Swagger) and Swagger executed panels for per-run pending approvals, execute, safe-stop, replan success/conflict and approval approve/reject. Full `mvn -B clean verify` finished 2026-10-17 00:59:33 +05:30: 149 tests, 144 passed, 5 skipped; 16 Cucumber scenarios/76 steps passed. Latest OSV scan reports 98 runtime artifacts, 2 accepted critical Spring findings, 0 unaccepted; working-tree secret scan (01:40) reports 236 files, 0 unreadable, 0 unallowed, 4 exact synthetic fixtures. Production auth screenshots remain blocked; synthetic-JWT tests are not live IdP evidence. No Java application behavior changed in this remediation.

The documentation presentation pass added `docs/DEPLOYMENT.md` and consolidated release evidence, operational risks, product decisions, ownership, and verification criteria in `docs/OPERATIONS_AND_RELEASE_READINESS.md`. Follow-up `mvn -B test` completed on 2026-10-17 with 149 tests, 144 passed, 0 failures/errors, and 5 skipped. Markdown validation checked 280 local links across 28 repository Markdown files with no missing targets. No application behavior or test logic changed.

Genuine screenshots now include the final Maven summary, generated Cucumber report, and browser images of actual CLI transcripts for greenfield, brownfield approval/safe-stop/resume, ambiguous approval/resume, and ambiguous rejection. Terminal-window captures are not included.

## Verified functionality (2026-10-17 final run: 149 tests, 144 passed, 5 skipped; BDD 16/76)

- Shortener: create, redirect, expiry (410), analytics, validation; load smoke keeps click count exact.
- Engine: DAG, parallel/sequential, synchronization, bounded retries, fallback, compensation, safe-stop/resume, replanning guards, audit chain, metrics; duplicate execution rejected (H2 contract test).
- Approvals: pending, approve, reject, resume, double decision 409 (JUnit, BDD, CLI).
- Security: `prod` OIDC/JWT with synthetic-token and local-RSA-key tests; fail-closed config tests. Not live.
- Persistence: Flyway + schema validation on H2; PostgreSQL contract test written, skipped (no Docker).
- Supply chain: OSV scan remediated (2 accepted), secret scan clean (working tree).
- API evidence: local loopback smoke 64/64 with response-body, redirect `Location`, click-count, and persisted lifecycle assertions; URL journey, approvals/rejection/safe-stop, invalid/not-found/conflict/expiry cases, docs and Actuator routes. Full route map and screenshot provenance in `evidence/api-tests/`.

## Outstanding

- Live OIDC IdP and real PostgreSQL runs; first CI run; git-history secret scan.
- Crash recovery, approval expiry, idempotency keys, leases; TLS/rate limiting; independent security review; soak testing.
- Direct CLI terminal UI screenshots (REQ-23 Partial); live production auth screenshots blocked by lack of IdP/PostgreSQL; symlink assertion on Windows.
- No live LLM provider; mock only.

## Known limitations

See [docs/RISKS_TRADEOFFS.md](../docs/RISKS_TRADEOFFS.md), [docs/ASSESSMENT_READINESS.md](../docs/ASSESSMENT_READINESS.md), [docs/HARDENING_REVIEW.md](../docs/HARDENING_REVIEW.md). Latest test status: 144 passed, 0 failed, 0 errors, 5 skipped in 149 tests (1 Windows symlink, 4 PostgreSQL/Docker).
