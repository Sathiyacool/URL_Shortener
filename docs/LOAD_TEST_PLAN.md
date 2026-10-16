# Load-test plan and results

## Purpose and limits

Guard the critical URL and orchestration paths against correctness regressions under concurrency. This is **not** a capacity or soak test and is not evidence of production performance: it uses the default profile (H2 in memory, no authentication, loopback, client and server on the same machine).

## Automated test

[`LoadSmokeTest`](../src/test/java/com/schwab/agentic/load/LoadSmokeTest.java) runs inside `mvn verify` (about 35 seconds) against an embedded server on a random port with a real HTTP client (8 threads):

| Phase | Requests | Expected |
|---|---|---|
| `POST /api/urls` | 200 | all 201 |
| `GET /{code}` on one hot link | 200 | all 302, and `totalClicks` must equal exactly 200 afterwards (no lost or duplicated click events) |
| `POST /api/scenarios/greenfield/run` (PARALLEL) | 16 | all 200 |

Assertions: zero unexpected statuses, each phase under 120 s, and exact click count. There are deliberately **no latency thresholds**, because they would be host-dependent and flaky. Results are written to `target/load-test-results.txt`.

Run alone: `mvn -B -Dtest=LoadSmokeTest test`.

## Recorded result

Machine: Windows 11 amd64, 4 logical CPUs, Java 21.0.12.1 (Temurin), Spring Boot 3.5.16, run on 2026-10-17 during the final `mvn -B clean verify`. The unchanged raw output was copied from `target/load-test-results.txt` at 2026-10-17 00:58:38 +05:30 to [../evidence/load-test-results.txt](../evidence/load-test-results.txt).
Raw output: [../evidence/load-test-results.txt](../evidence/load-test-results.txt).

| Phase | Failures | Throughput | p50 | p95 | max |
|---|---|---|---|---|---|
| create-url (200) | 0 | 64.7 req/s | 82.4 ms | 364.4 ms | 903.5 ms |
| redirect (200) | 0 | 161.8 req/s | 34.0 ms | 97.3 ms | 554.7 ms |
| greenfield-run (16) | 0 | 1.9 req/s | 3660.3 ms | 5207.5 ms | 5207.5 ms |

Click analytics recorded exactly 200 of 200 redirects. Greenfield runs are slow because each run executes the full task DAG, with durable state transitions and audit hashing per step on an in-memory database, 8 at a time on 4 CPUs. Create-URL tail latency included JIT/warm-up. A single run is one sample; treat the numbers as indicative only.

## Larger-scale validation plan

Before any production claim, on a production-like environment (PostgreSQL, TLS gateway, real OIDC tokens):

1. Tool: k6 or Gatling against the packaged JAR; separate load generator host.
2. Workloads: sustained redirects (read-heavy, e.g. 90/10 redirect/create), orchestration approval workflow at low rate, mixed.
3. Targets to agree with the owner: redirect p95/p99, error rate, max concurrent orchestration runs, database connection pool saturation.
4. Observe: Hikari pool, GC, database locks (`findByIdForUpdate`), click-insert rate, audit-chain write contention.
5. Record machine specs, dataset size, duration, warm-up, and commit hash with every result.

Known risk areas to probe: click events are written synchronously on the redirect path; the run row lock serializes work per run; the audit hash chain is sequential per run.
