# Assessment scenarios

Each scenario has a fixed task DAG and input, uses the deterministic mock LLM, and can be launched through REST or the Java CLI. The brownfield scenario performs real Java checks against the shortener; it does not invoke shell commands or arbitrary model-selected tests.

**Deployment context:** the scenarios provide reproducible assessment workflows using the deterministic mock provider and local test profile. The `prod` profile targets PostgreSQL and OIDC/JWT; environment-specific integration evidence is maintained in [operational release readiness](./OPERATIONS_AND_RELEASE_READINESS.md).

## Starting the application

Set JDK 21 and Maven paths as shown in the README, then run:

```powershell
mvn spring-boot:run
```

The application listens at `http://localhost:8080`. List available scenarios with `GET /api/scenarios`.

To run from the CLI at application startup, pass `--scenario` and optionally `--mode`:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--scenario=greenfield --mode=SEQUENTIAL"
```

Supported IDs are `greenfield`, `brownfield`, and `ambiguous`; execution modes are `SEQUENTIAL` and `PARALLEL`. CLI startup also starts the REST server, allowing the same process to receive a human approval and a resume request. In-memory H2 data is discarded when the process exits.

For an isolated CLI run that exits after printing the result, add `--spring.main.web-application-type=none`. Without further options a pending approval is only reported, because the in-memory database vanishes when the process exits.

### Complete approval lifecycle from the CLI (headless)

Add an explicit human decision and the same process approves or rejects through `ApprovalService`, then resumes through `OrchestrationRunService`, exactly as the REST path does:

```powershell
# approve and resume to completion
mvn spring-boot:run "-Dspring-boot.run.arguments=--scenario=brownfield --mode=SEQUENTIAL --spring.main.web-application-type=none --decision=APPROVE --approver=cli-reviewer --rationale=Approved-30-day-default"
# reject: run ends CANCELLED and the gated task never runs
mvn spring-boot:run "-Dspring-boot.run.arguments=--scenario=brownfield --mode=SEQUENTIAL --spring.main.web-application-type=none --decision=REJECT --approver=cli-reviewer --rationale=Needs-review"
# approve, request safe-stop (run halts SAFE_STOPPED), then resume to completion
mvn spring-boot:run "-Dspring-boot.run.arguments=--scenario=brownfield --mode=SEQUENTIAL --spring.main.web-application-type=none --decision=APPROVE --approver=cli-reviewer --rationale=Approved --safe-stop-before-resume"
# ambiguous requirements, parallel mode
mvn spring-boot:run "-Dspring-boot.run.arguments=--scenario=ambiguous --mode=PARALLEL --spring.main.web-application-type=none --decision=APPROVE --approver=cli-reviewer --rationale=Clarified"
```

Use rationale values without spaces on Windows, or quote them carefully. `--decision` requires `--approver`; `--safe-stop-before-resume` requires `--decision=APPROVE`. Real outputs are in `evidence/cli-*.txt`.

The default profile binds only to loopback and is unauthenticated. In the `prod` profile, authenticate scenario/run/approval requests with an OIDC JWT matching the configured HTTPS issuer and audience and carrying `agentic.operator`; terminate TLS and enforce network/rate controls at a trusted gateway. Production-profile tests use MockMvc test tokens, not a live identity provider.

Headless commands (each starts a fresh in-memory database):

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--scenario=greenfield --mode=SEQUENTIAL --spring.main.web-application-type=none"
mvn spring-boot:run "-Dspring-boot.run.arguments=--scenario=brownfield --mode=SEQUENTIAL --spring.main.web-application-type=none"
mvn spring-boot:run "-Dspring-boot.run.arguments=--scenario=ambiguous --mode=PARALLEL --spring.main.web-application-type=none"
```

## Greenfield

**Goal:** Turn a blank-slate URL-shortener requirement into sandboxed proposals and validation artifacts.

**DAG:** requirements → architecture → implementation proposal → tests and independent review/security tasks → documentation.

**Run:** `POST /api/scenarios/greenfield/run` with optional body `{"executionMode":"PARALLEL"}`. The default mode is parallel. This scenario contains no HIGH-impact task, so it should finish without an approval pause.

## Brownfield: default URL expiration

**Goal:** Establish the existing application baseline before proposing a scoped enhancement, then demonstrate a regression test.

**Change:** The shortener uses `app.default-expiration: 30d` when the request omits `expiresAt`. A caller-provided future expiry takes precedence. This behavior is implemented in `ShortUrlService`.

**DAG:** baseline validation → requirements → architecture → HIGH-impact implementation proposal → expiration regression → review/security → documentation. Implementation is blocked until a human approves it.

The baseline Java check creates a URL, resolves it, and verifies click analytics. After approval, the regression check verifies the configured default TTL, verifies an explicit expiry path through service-level coverage, rejects an expired URL, and confirms that the rejected redirect records no click. Unit and MockMvc shortener tests cover explicit expiration and the HTTP behavior.

**REST walkthrough:**

1. `POST /api/scenarios/brownfield/run` with `{"executionMode":"SEQUENTIAL"}`.
2. The response should have `status: "AWAITING_APPROVAL"` and a pending approval for `implementation`; baseline validation has already completed.
3. `POST /api/approvals/{approvalId}` with `{"decision":"APPROVE","approver":"reviewer","rationale":"Approved the 30-day default and explicit-expiry precedence"}`.
4. `POST /api/runs/{runId}/resume` with `{"mode":"SEQUENTIAL"}`.
5. Inspect `GET /api/runs/{runId}`, `GET /api/runs/{runId}/audit`, and `GET /api/runs/{runId}/audit/verify`; the run should complete and its event chain should verify.

The human rationale is passed to the approved task as agent context. Rejecting the approval prevents implementation dispatch.

## Ambiguous requirements

**Goal:** Prevent agents from silently inventing an unspecified retention policy.

**Input:** The requested retention period and behavior for existing links are deliberately unspecified.

**DAG:** requirements → HIGH-impact human clarification checkpoint → implementation proposal → validation → documentation.

**Run:** `POST /api/scenarios/ambiguous/run`. It should pause with a pending approval for `human-clarification`. Approve with a rationale containing the selected retention behavior and then call the run's `/resume` endpoint, or reject to cancel the proposed work. The human rationale becomes context for downstream agent tasks.

## Reproducibility and boundaries

- Inputs, task IDs, dependency edges, impact levels, and registered check mappings are defined in Java in `ScenarioCatalog` and the scenario check handler.
- Artifacts are written to the configured sandbox and versioned; they do not modify application source.
- Registered checks are selected from a closed Java enum/map. A model response cannot add or execute a command/check.
- The H2 database is ephemeral by default. Keep one application process running from run creation through approval, resume, and audit inspection.
