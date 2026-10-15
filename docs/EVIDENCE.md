# Assessment evidence index

Last updated: 2026-10-16 after production-profile hardening. Requirement IDs refer to [TRACEABILITY.md](./TRACEABILITY.md). Everything below is real output; nothing is fabricated.

## Automated evidence

| Evidence | Actual result | Location | Requirements |
|---|---|---|---|
| Clean Maven verification after PostgreSQL/OIDC changes | `mvn clean verify`: 111 tests, 0 failures, 0 errors, 1 skipped; `BUILD SUCCESS` | [production-hardening-test-summary.txt](../evidence/production-hardening-test-summary.txt); `target/surefire-reports/` (ignored, regenerated) | all |
| PostgreSQL migration/schema mapping | Flyway migration and Hibernate `validate` run successfully against H2 during the full suite | Surefire reports; H2 only, not a live PostgreSQL test | REQ-13 (partial) |
| OIDC authorization boundaries | Anonymous 401, JWT without operator scope 403, configured operator scope permitted, missing OIDC issuer/audience fails closed; 8 focused tests passed | Surefire XML for `Production*Test` and `SecurityConfigurationTest` | REQ-13 |
| Production database fail-closed startup | Real Java process exited 1 with all DB/OIDC environment variables empty and rejected the missing PostgreSQL configuration before starting | [prod-00-fail-closed-database.txt](../evidence/prod-00-fail-closed-database.txt) | REQ-13 |
| BDD | 6 scenarios and 22 steps passed; 0 failed; 0 skipped | `target/cucumber-reports/cucumber.html`, `cucumber.json` (ignored, regenerated) | REQ-01, 02, 08, 17 |
| Symlink escape | 4 discovered, 3 executed, 1 skipped (Windows privilege) | `TEST-com.schwab.agentic.guardrails.ArtifactSandboxTest.xml` | REQ-12 (Partial) |

## CLI scenario transcripts (real headless runs)

Commands use `--spring.main.web-application-type=none`, the mock provider and a fresh in-memory H2 per process. New options: `--decision=APPROVE|REJECT --approver=<name> [--rationale=<text>] [--safe-stop-before-resume]`. They are always supplied by a human operator; nothing is approved automatically.

| Transcript | Scenario and mode | Observed result |
|---|---|---|
| [cli-01-greenfield.txt](../evidence/cli-01-greenfield.txt) | greenfield, sequential | `SUCCEEDED` |
| [cli-02-brownfield-pending-only.txt](../evidence/cli-02-brownfield-pending-only.txt) | brownfield, sequential | `AWAITING_APPROVAL` for `implementation` |
| [cli-03-brownfield-approve.txt](../evidence/cli-03-brownfield-approve.txt) | brownfield, approve | decision `APPROVED` by `cli-reviewer`, resumed, `SUCCEEDED` |
| [cli-04-brownfield-reject.txt](../evidence/cli-04-brownfield-reject.txt) | brownfield, reject | decision `REJECTED`, run `CANCELLED` |
| [cli-05-brownfield-approve-safe-stop-resume.txt](../evidence/cli-05-brownfield-approve-safe-stop-resume.txt) | brownfield, approve plus safe-stop | `SAFE_STOPPED`, then resumed, `SUCCEEDED` |
| [cli-06-ambiguous-pending-only.txt](../evidence/cli-06-ambiguous-pending-only.txt) | ambiguous, parallel | `AWAITING_APPROVAL` for `human-clarification` |
| [cli-07-ambiguous-approve.txt](../evidence/cli-07-ambiguous-approve.txt) | ambiguous, approve | `APPROVED`, resumed, `SUCCEEDED` |
| [cli-08-ambiguous-reject.txt](../evidence/cli-08-ambiguous-reject.txt) | ambiguous, reject | `REJECTED`, run `CANCELLED` |

The same lifecycles are asserted in `ScenarioLifecycleIntegrationTest`, including `verifyAudit` hash-chain validity. A CLI run ID is valid only for its own process, because H2 is in memory.

## Historical REST/security transcripts (previous HTTP Basic production profile)

These are genuine records from the previous app configuration, but they are **not evidence for the current OIDC/PostgreSQL profile** and must not be used to claim its live behavior.

| Transcript | Historical result |
|---|---|
| [rest-00-prod-fail-closed.txt](../evidence/rest-00-prod-fail-closed.txt) | Previous Basic-auth config failed startup without `APP_SECURITY_USERNAME`/`APP_SECURITY_PASSWORD` |
| [rest-01-prod-security-checks.txt](../evidence/rest-01-prod-security-checks.txt) | 19 of 19 checks matched against the prior Basic-auth configuration |

Current production authentication is configured for OIDC JWT and PostgreSQL. No live OIDC issuer or PostgreSQL server was available, so both remain integration/deployment verification gaps.

## Screenshots: NOT CAPTURED

No usable screenshots exist, and none are claimed. An attempt to render `target/cucumber-reports/cucumber.html` in the embedded browser produced a clipped, unusable image, which was discarded. The terminal windows and a real Maven console cannot be captured from this environment. Missing items:

| Needed screenshot | Status |
|---|---|
| Final `mvn clean verify` summary | Missing |
| Cucumber HTML report (6 scenarios, 22 steps) | Missing |
| Greenfield, brownfield and ambiguous CLI runs, including approval lifecycle | Missing (text transcripts exist) |
| REST and security checks | Missing (text transcript exists) |

Precise Windows steps to capture them:

1. Open PowerShell in the repository and set the JDK/Maven paths from the README. Run `mvn clean verify`. When `BUILD SUCCESS` and `Tests run: 111, Failures: 0, Errors: 0, Skipped: 1` appear, press `Win+Shift+S` and save as `evidence\screenshots\01-maven-summary.png`.
2. Open `target\cucumber-reports\cucumber.html` in Edge, expand both features, press `Win+Shift+S` and save as `02-cucumber-report.png`.
3. Run each command in [SCENARIOS.md](./SCENARIOS.md) and the lifecycle commands, capturing each terminal as `03-...png`.
4. For current production auth evidence, use a controlled test issuer that signs a JWT with the configured audience and `agentic.operator` scope; configure a test PostgreSQL service and run requests through the TLS gateway. Do not use the old `rest-01-prod-security-checks.txt` HTTP Basic transcript as evidence for the current profile. Redact tokens before any screenshot.
5. Redact credentials and unrelated data before submission. Do not commit screenshots containing secrets.

## Local-only evidence

The generated `agent-workspace/` directories were preserved and remain ignored by `.gitignore`.
