package com.schwab.agentic.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Modest repeatable load smoke test over real HTTP (embedded server, in-memory H2, default profile).
 * It is a regression guard for correctness under concurrency, not a capacity benchmark: thresholds are
 * deliberately generous and results depend on the host. Metrics are written to target/load-test-results.txt.
 * See docs/LOAD_TEST_PLAN.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoadSmokeTest {

    private static final int THREADS = 8;
    private static final int CREATES = 200;
    private static final int REDIRECTS_ON_ONE_LINK = 200;
    private static final int SCENARIO_RUNS = 16;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void criticalUrlAndOrchestrationPathsStayCorrectUnderConcurrency() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            Phase create = run(pool, "create-url", CREATES, i -> post("/api/urls",
                    "{\"url\":\"https://example.com/load/" + i + "\"}"), 201);

            JsonNode link = objectMapper.readTree(post("/api/urls", "{\"url\":\"https://example.com/hot\"}").body());
            String code = link.path("code").asText();
            Phase redirect = run(pool, "redirect", REDIRECTS_ON_ONE_LINK, i -> get("/" + code), 302);

            Phase scenarios = run(pool, "greenfield-run", SCENARIO_RUNS, i -> post(
                    "/api/scenarios/greenfield/run", "{\"executionMode\":\"PARALLEL\"}"), 200);

            JsonNode stats = objectMapper.readTree(get("/api/urls/" + code + "/stats").body());
            assertEquals(REDIRECTS_ON_ONE_LINK, stats.path("totalClicks").asLong(),
                    "Every successful redirect must be recorded exactly once under concurrency");

            String report = String.join(System.lineSeparator(),
                    "Load smoke test results (default profile, H2 in-memory, embedded server)",
                    "host=" + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                            + " cpus=" + Runtime.getRuntime().availableProcessors()
                            + " java=" + System.getProperty("java.version") + " clientThreads=" + THREADS,
                    create.summary(), redirect.summary(), scenarios.summary(),
                    "analytics totalClicks=" + stats.path("totalClicks").asLong() + " expected=" + REDIRECTS_ON_ONE_LINK);
            Files.writeString(Path.of("target", "load-test-results.txt"), report);
            System.out.println(report);
        } finally {
            pool.shutdownNow();
        }
    }

    private record Phase(String name, int requests, int failures, long wallMillis, List<Long> latenciesMicros) {
        String summary() {
            List<Long> sorted = new ArrayList<>(latenciesMicros);
            Collections.sort(sorted);
            double seconds = Math.max(wallMillis, 1) / 1000.0;
            return String.format("%s: requests=%d failures=%d wall=%dms throughput=%.1f req/s p50=%.1fms p95=%.1fms max=%.1fms",
                    name, requests, failures, wallMillis, requests / seconds,
                    percentile(sorted, 0.50), percentile(sorted, 0.95), percentile(sorted, 1.0));
        }

        private static double percentile(List<Long> sorted, double p) {
            int index = Math.min(sorted.size() - 1, (int) Math.ceil(p * sorted.size()) - 1);
            return sorted.get(Math.max(index, 0)) / 1000.0;
        }
    }

    private Phase run(ExecutorService pool, String name, int count, Request request, int expectedStatus)
            throws Exception {
        long begin = System.nanoTime();
        List<Future<long[]>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int index = i;
            Callable<long[]> task = () -> {
                long start = System.nanoTime();
                HttpResponse<String> response = request.send(index);
                return new long[] {response.statusCode() == expectedStatus ? 0 : 1, (System.nanoTime() - start) / 1000};
            };
            futures.add(pool.submit(task));
        }
        int failures = 0;
        List<Long> latencies = new ArrayList<>();
        for (Future<long[]> future : futures) {
            long[] result = future.get();
            failures += (int) result[0];
            latencies.add(result[1]);
        }
        Phase phase = new Phase(name, count, failures, (System.nanoTime() - begin) / 1_000_000, latencies);
        assertEquals(0, phase.failures(), name + " returned unexpected statuses");
        assertTrue(phase.wallMillis() < 120_000, name + " took unreasonably long");
        return phase;
    }

    @FunctionalInterface
    private interface Request {
        HttpResponse<String> send(int index) throws Exception;
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
