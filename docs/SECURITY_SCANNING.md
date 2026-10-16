# Dependency and secret scanning

Last run: **2026-10-17**, Windows 11, JDK 21.0.12.1, Maven 3.9.11. Nothing here is a substitute for a managed SCA/SAST service.

Deployment controls and security responsibilities are described in [DEPLOYMENT.md](./DEPLOYMENT.md) and [OPERATIONS_AND_RELEASE_READINESS.md](./OPERATIONS_AND_RELEASE_READINESS.md).

## How to run

```powershell
# Dependency scan (needs Maven on PATH and HTTPS access to api.osv.dev). Exits non-zero on unaccepted findings.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\osv-scan.ps1 -OutFile evidence\dependency-scan-osv.txt `
  -AcceptedIds GHSA-j9f9-w8pj-32f8,GHSA-pc63-qcmh-9cmg

# Working-tree secret scan (regex based). Exits non-zero on unallowed matches.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\secret-scan.ps1 -OutFile evidence\secret-scan-local.txt
```

`-ExecutionPolicy Bypass` is process-scoped; the machine policy is not changed. CI runs the same scripts and, additionally, gitleaks over the full git history ([../.github/workflows/ci.yml](../.github/workflows/ci.yml)). The CI jobs have **not** executed yet.

## Dependency scan (OSV, runtime scope, 98 artifacts)

Method: `mvn dependency:list` resolves exact versions; each is queried against the OSV database. OSV covers advisories published there; it does not analyze reachability and is not a complete CVE feed.

**First run: 19 advisories** (3 critical Tomcat, 2 critical Spring Framework, several high Jackson, one high PostgreSQL JDBC, and moderate Jackson/commons-lang3/log4j-api).

**Remediation applied** in `pom.xml` (patch-level overrides of Spring Boot 3.5.16 managed versions; the full test suite was rerun afterwards):

| Property | From | To | Advisories resolved |
|---|---|---|---|
| `jackson-bom.version` | 2.21.4 | 2.21.7 | jackson-core (2), jackson-databind (8) |
| `tomcat.version` | 10.1.55 | 10.1.61 | 3 critical Tomcat (fixed in 10.1.58; 10.1.58 is not on Maven Central, so 10.1.61 was used) |
| `postgresql.version` | 42.7.11 | 42.7.12 | channel-binding downgrade |
| `commons-lang3.version` | 3.17.0 | 3.18.0 | uncontrolled recursion |
| `log4j2.version` | 2.24.3 | 2.25.5 | log4j-api non-finite float encoding |

**Rescan: 0 unaccepted findings, 2 accepted advisories**, both affecting the packaged `org.springframework:spring-webmvc:6.2.19`:

| Advisory | Published affected range / applicability | Risk treatment and review trigger |
|---|---|---|
| [GHSA-j9f9-w8pj-32f8](https://github.com/advisories/GHSA-j9f9-w8pj-32f8), SSE stream corruption while rendering fragments | Spring Framework 6.2.0–6.2.19 is affected. The application has no `SseEmitter`, `ServerSentEvent`, or `text/event-stream` use in `src/main`; the API returns ordinary HTTP responses. This is a reachability assessment, not proof that the vulnerable code is unreachable. | Keep the CRITICAL finding visible and retain the JSON-only/no-SSE constraint. Recheck on every Spring Boot patch and before production promotion; upgrade when a supported Boot 3 release manages a fixed Spring 6.2.x version. |
| [GHSA-pc63-qcmh-9cmg](https://github.com/advisories/GHSA-pc63-qcmh-9cmg), XsltView path limitation | Spring Framework 6.2.0–6.2.19 is affected. The application does not use `XsltView` or server-rendered views and does not accept user-supplied XSLT. This narrows apparent applicability but does not remove the vulnerable component from the packaged classpath. | Keep the CRITICAL finding visible and avoid introducing `XsltView` or user-controlled templates. Recheck on every Spring Boot patch and before production promotion; upgrade when a supported Boot 3 release manages a fixed Spring 6.2.x version. |

**Compatibility decision (verified 2026-10-17):** the Spring Boot 3.5.16 BOM manages Spring Framework 6.2.19. Maven Central had no published Spring Framework 6.2.20 artifact at verification time; the advisories list Spring Framework 7.0.9 as fixed, which is outside this Boot 3.5 dependency line. No compatible patch-level change was available, and forcing a different/unpublished version or making an untested major Boot migration was not considered safe. No Spring version override was made. The findings remain accepted, not resolved or suppressed.

**Owner/action:** the project maintainer and release approver must re-evaluate both advisories when Spring Framework 6.2.20 or a later supported Boot 3.5 patch becomes available, and again before any production release. Upgrade through the Boot-managed dependency set, then rerun full verification and OSV scanning. If no supported Boot 3 fix is published before production is considered, record a new explicit risk acceptance or defer release; assess the Boot 4 migration separately.

Limits: test-scope and build-plugin dependencies were not scanned; no container image exists to scan; the scan needs network access and reflects the OSV database on the run date.

## Secret scan

- Working tree: **236 candidate files, 0 unreadable files, 0 unallowed matches, 4 allowed fixtures** (2026-10-17 01:40). The script explicitly excludes generated/dependency directories and binary extensions; see `$excludedDirectories` and `$excludedExtensions` in `scripts/secret-scan.ps1` for the exact scope. The four matches are deliberately fake literals in tests (`PolicyEngineTest` tests detection of a fake `api_key`; three `Production*Test` classes set dummy `app.database.password` values for fail-closed/OIDC tests). They are allowlisted by exact file, line, detector, and content fingerprint and printed as redacted `ALLOWED` records.
- Git history: **not scanned locally** (gitleaks is not installed). Covered only by the CI `secret-scan` job once it runs. Until then, history is unverified.
- `.gitignore` excludes `.env*` (except `.env.example`).
- A regex scan can miss secrets that do not match its patterns.
