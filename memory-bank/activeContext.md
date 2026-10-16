# Active context

Date of last verification: **2026-10-17**. Always inspect the current `git status`/diff; rerun relevant checks before publication. The latest build and API smoke are actual executions against the current worktree.

## Last verified results (executed, not assumed)

| Check | Result |
|---|---|
| `mvn -B clean verify` (final rerun, 2026-10-17 00:59:33 +05:30) | **149 tests, 0 failures, 0 errors, 5 skipped (144 passed), BUILD SUCCESS, exit 0** |
| Skips | 1 `ArtifactSandboxTest.rejectsSymlinkThatTargetsOutsideSandbox` (Windows denied symlink creation; assertion not executed); 4 `PostgresPersistenceContractTest` (Docker not running; same contract passed on H2) |
| BDD (Cucumber) | **16 scenarios, 76 steps; 0 failed, 0 skipped** (3 features incl. `orchestration_lifecycle.feature`) |
| CLI | 8 headless runs (`evidence/cli-01..08`) cover greenfield, brownfield/ambiguous pending, approve, reject, safe-stop+resume; `ScenarioCliRunnerTest` (10) covers argument parsing |
| Production authorization | MockMvc synthetic JWTs (401/403/200) and `OidcLocalKeyValidationTest` (local RSA key; signature/expiry/issuer/audience/tamper). **Not live.** See `docs/HARDENING_REVIEW.md` |
| Local API endpoint smoke | **64 checks passed, 0 failed** against 17 OpenAPI operations plus generated OpenAPI/Swagger and Actuator routes; response fields, expiry, redirect Location, click increment, persisted lifecycle states and error bodies asserted; default profile, H2, loopback. Transcript and endpoint/screenshot inventory in `evidence/api-tests/` |
| Focused production auth tests | **7 passed, 0 failed/errors/skipped** (`ProductionAuthorizationBoundaryTest` 4, `ProductionSecurityIntegrationTest` 2, `ProductionFailClosedTest` 1); synthetic JWT / MockMvc only |
| Dependency scan (OSV) | 19 advisories found, patched via `pom.xml` overrides; 0 unaccepted, 2 accepted critical Spring Framework advisories on 6.2.19; no supported fixed Spring 6.2.x version was available with Boot 3.5.16 at verification - `docs/SECURITY_SCANNING.md` |
| Secret scan | working tree (2026-10-17 01:40): 236 files, 0 unreadable, 0 unallowed, 4 exact synthetic fixtures. Git history (gitleaks) not run |
| Load smoke (`LoadSmokeTest`) | 416 requests, 0 failures, exactly 200/200 clicks; create 64.7 req/s (p95 364.4 ms), redirect 161.8 req/s (p95 97.3 ms), 16 parallel runs (p95 5207.5 ms); 4-CPU Windows host, H2 - `docs/LOAD_TEST_PLAN.md` |
| Screenshots | 11 general captures in `evidence/screenshots/` include the final Maven summary, BDD report, and genuine browser captures of CLI transcripts; 40 API screenshots are indexed in `evidence/api-tests/test-summary.md` (direct redirect 302 and orchestration response captures added 2026-10-17). Direct CLI terminal UI and live production auth screenshots remain missing/blocked |
| CI | `.github/workflows/ci.yml` uses Node 24 action releases pinned to SHAs; local `actionlint` v1.7.12 passed; GitHub Actions itself **never run** (nothing pushed) |
| Publication | Commit `d7ecc18` is on `origin/main`; P15 changes are **uncommitted** and need user approval to commit/push |

## Current state

P0-P16 are complete locally and remain uncommitted. The current final `mvn -B clean verify` passed on 2026-10-17 at 00:59:33 +05:30 (149 total, 144 passed, 5 skipped); all 16 Cucumber scenarios/76 steps passed. The API evidence smoke passed 64/64 real HTTP checks. The latest saved OSV scan has 98 runtime artifacts, 2 accepted critical `spring-webmvc:6.2.19` findings, and 0 unaccepted findings; the working-tree secret scan reports 236 files, 0 unreadable, 0 unallowed, 4 exact fixtures. P16 consists of PowerShell smoke/evidence/docs changes; no Java application behavior changed in this remediation. The only P15 production-code change was extraction of `SecurityConfiguration.productionTokenValidator` (behavior unchanged).

Documentation presentation was consolidated on 2026-10-17: deployment setup is in `docs/DEPLOYMENT.md`; operational risks, decisions, owners, and release criteria are maintained in `docs/OPERATIONS_AND_RELEASE_READINESS.md`. Follow-up `mvn -B test` completed with 149 tests, 144 passed, 0 failures/errors, and 5 skipped; repository Markdown link validation checked 280 local links across 28 Markdown files with zero missing targets. No application behavior or tests were changed.

## Unresolved issues and gaps

- Live OIDC IdP test (discovery, JWKS, rotation, outage, real claim shapes) not done.
- PostgreSQL Testcontainers test not executed here (needs Docker); first CI run should show it.
- No crash recovery for RUNNING/RETRYING, no approval expiry, no idempotency keys, no distributed leases (`docs/HARDENING_REVIEW.md`).
- 2 accepted critical Spring Framework advisories remain on `spring-webmvc:6.2.19`; no published 6.2.20 or supported Boot 3.5 patch was available at verification. Applicability, mitigation, owner, and review trigger are documented in `docs/SECURITY_SCANNING.md`.
- Direct CLI terminal UI screenshots missing; live production auth screenshots blocked; symlink assertion unexecuted on Windows.
- Direct screenshots of the redirect's raw `302`/`Location` and selected orchestration request/response panels (per-run pending approvals, execute, replan, and approval decision) were captured on 2026-10-17 (see `evidence/api-tests/test-summary.md`); the redirect capture is curl.exe, not Swagger.
- Deployment and release verification remains environment-specific; TLS/rate limits, live integrations, independent security review, and soak/failure testing are tracked in `docs/OPERATIONS_AND_RELEASE_READINESS.md`.

## Next recommended actions

1. With user approval, commit and push P15; check the first GitHub Actions run (build, Postgres test, OSV, gitleaks) and record the result.
2. Run `PostgresPersistenceContractTest` on a Docker host; run a live-IdP token test.
3. Decide design for crash recovery and approval expiry before implementing.
4. If terminal-window rather than transcript screenshots are required, capture the CLI terminal UI per `docs/EVIDENCE.md`; production auth screenshots require the live test environment.
