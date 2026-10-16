# Tech context

## Stack (verified from `pom.xml`)

Java 21 (Temurin 21.0.12), Spring Boot 3.5.16, Maven 3.9.11, Spring Web, Spring Data JPA, Validation, Actuator, Spring Security/OAuth2 Resource Server (plus spring-security-test), springdoc-openapi 2.9.1, H2, PostgreSQL JDBC, Flyway Core/PostgreSQL support, JUnit 5, Mockito, Cucumber 7.25.0 with the JUnit Platform Suite. The JUnit BOM 5.13.0 is imported so Jupiter and the Platform versions align.

## Configuration

- Default profile: H2 in-memory (state is lost per process; CLI run IDs are valid only within their process), binds `127.0.0.1`, unauthenticated for local demo only.
- `prod`: PostgreSQL (`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`), Flyway migrations and Hibernate `ddl-auto=validate`; TLS mode defaults to `verify-full` (`DATABASE_SSL_MODE`); OIDC JWT (`APP_OIDC_ISSUER_URI` HTTPS and `APP_OIDC_AUDIENCE`) with required `agentic.operator` scope. Secret values must come from a secret manager/process environment and never be committed.
- OpenAPI JSON and Swagger UI are enabled by the default configuration; the `prod` profile disables them and the production authorization chain denies their routes.
- Production configuration is not live-service verified; do not call the application production-ready.

## Windows setup (PowerShell)

```powershell
$env:JAVA_HOME="$env:LOCALAPPDATA\Programs\EclipseAdoptium\jdk-21.0.12.1+1"
$env:Path="$env:LOCALAPPDATA\Programs\Apache\Maven\apache-maven-3.9.11\bin;$env:JAVA_HOME\bin;$env:Path"
```

## Commands

```powershell
mvn clean verify                      # full build and all tests, including BDD
mvn test -Dtest=RunCucumberTest       # BDD only
mvn spring-boot:run                   # local server
mvn spring-boot:run "-Dspring-boot.run.arguments=--scenario=brownfield --decision=APPROVE --approver=reviewer --rationale=ok --spring.main.web-application-type=none"
```

CLI notes: headless mode needs `--spring.main.web-application-type=none`; avoid spaces in `--rationale` on Windows; optional `--safe-stop-before-resume`. Scenario IDs are listed in [docs/SCENARIOS.md](../docs/SCENARIOS.md).

## Windows-specific limitations

- `ArtifactSandboxTest.rejectsSymlinkThatTargetsOutsideSandbox` is **skipped** when Windows denies symlink creation. Its escape assertion was not executed on this machine. Enable Developer Mode (or run elevated) to execute it. Do not weaken the test.
- PowerShell strips quotes in inline JSON passed to `curl.exe`; use `-d @file.json`.
- Harmless warnings: Mockito JDK 21 dynamic-agent warning; Cucumber "classpath resource selector 'features'".

Reports: `target/surefire-reports/`, `target/cucumber-reports/` (`cucumber.html`, `.json`). `target/` is git-ignored.
