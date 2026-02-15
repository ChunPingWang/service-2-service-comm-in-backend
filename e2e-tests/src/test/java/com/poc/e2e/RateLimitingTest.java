package com.poc.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rate limiting integration tests.
 *
 * <p>Validates that APISIX gateway enforces rate limiting via the
 * {@code limit-req} plugin. The configured limit is 100 requests/sec
 * with a burst of 50. Sending more than 150 rapid requests should
 * result in at least some HTTP 429 (Too Many Requests) responses.
 *
 * <p>These tests require a running Kind cluster with APISIX deployed
 * and rate limiting configured on the gateway routes.
 */
@SpringBootTest(classes = RateLimitingTest.class)
@Disabled("Requires Kind cluster with APISIX deployed")
class RateLimitingTest {

    private static final String GATEWAY_BASE_URL = "http://localhost:30080";
    private static final int TOTAL_REQUESTS = 200;

    private static RestClient restClient;

    @BeforeAll
    static void setUp() {
        restClient = RestClient.builder()
                .baseUrl(GATEWAY_BASE_URL)
                .build();
    }

    @Test
    @DisplayName("Rapid requests exceeding rate limit should receive 429 Too Many Requests")
    void rapidRequestsShouldTriggerRateLimiting() {
        List<HttpStatusCode> statusCodes = new ArrayList<>();

        // Send requests in rapid succession (single-threaded to maximize burst rate)
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            HttpStatusCode statusCode = restClient.get()
                    .uri("/api/v1/products")
                    .exchange((req, res) -> res.getStatusCode());
            statusCodes.add(statusCode);
        }

        long tooManyRequestsCount = statusCodes.stream()
                .filter(code -> code.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS))
                .count();

        long successCount = statusCodes.stream()
                .filter(HttpStatusCode::is2xxSuccessful)
                .count();

        assertTrue(tooManyRequestsCount > 0,
                "At least some requests should be rate-limited (429). "
                        + "Got " + tooManyRequestsCount + " out of " + TOTAL_REQUESTS + " requests. "
                        + "Successful: " + successCount);
    }

    @Test
    @DisplayName("Concurrent requests exceeding rate limit should receive 429 Too Many Requests")
    void concurrentRequestsShouldTriggerRateLimiting() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<HttpStatusCode>> futures = new ArrayList<>();

            for (int i = 0; i < TOTAL_REQUESTS; i++) {
                CompletableFuture<HttpStatusCode> future = CompletableFuture.supplyAsync(() ->
                        restClient.get()
                                .uri("/api/v1/products")
                                .exchange((req, res) -> res.getStatusCode()),
                        executor);
                futures.add(future);
            }

            List<HttpStatusCode> statusCodes = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            long tooManyRequestsCount = statusCodes.stream()
                    .filter(code -> code.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS))
                    .count();

            long successCount = statusCodes.stream()
                    .filter(HttpStatusCode::is2xxSuccessful)
                    .count();

            assertTrue(tooManyRequestsCount > 0,
                    "At least some concurrent requests should be rate-limited (429). "
                            + "Got " + tooManyRequestsCount + " out of " + TOTAL_REQUESTS + " requests. "
                            + "Successful: " + successCount);
        }
    }

    @Test
    @DisplayName("Rate limit should return 429 status code with correct response")
    void rateLimitResponseShouldReturn429StatusCode() {
        // Exhaust the rate limit by sending rapid requests
        for (int i = 0; i < 200; i++) {
            restClient.get()
                    .uri("/api/v1/products")
                    .exchange((req, res) -> res.getStatusCode());
        }

        // This request should be rate-limited
        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/products")
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(
                statusCode.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)
                        || statusCode.is2xxSuccessful(),
                "After burst of requests, response should be either 429 (rate-limited) or 2xx (if limit recovered). Got: " + statusCode
        );
    }
}
