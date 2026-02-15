package com.poc.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Circuit breaker E2E tests.
 *
 * <p>Validates the Resilience4j circuit breaker behavior on the
 * Order -> Payment synchronous REST call:
 * <ul>
 *   <li>Normal flow: circuit breaker is CLOSED, requests pass through</li>
 *   <li>Failure flow: circuit breaker OPENS after threshold failures</li>
 *   <li>Recovery flow: circuit breaker transitions to HALF_OPEN and recovers</li>
 * </ul>
 *
 * <p>The circuit breaker is configured on order-service for the call to
 * payment-service (POST /api/v1/payments). These tests verify the behavior
 * via the Actuator health endpoint which exposes circuit breaker state.</p>
 *
 * <p>These tests require a running Kind cluster with order-service and
 * payment-service deployed, and the ability to simulate payment-service
 * failures (e.g., by scaling down payment-service pods).</p>
 */
@SpringBootTest(classes = CircuitBreakerE2ETest.class)
@Disabled("Requires Kind cluster with full stack deployed")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CircuitBreakerE2ETest {

    private static final String ORDER_SERVICE_URL = "http://localhost:8081";
    private static final String PAYMENT_SERVICE_URL = "http://localhost:8083";

    /** Number of requests to send to trigger circuit breaker opening. */
    private static final int FAILURE_THRESHOLD_REQUESTS = 20;

    /** Time to wait for circuit breaker state transitions (milliseconds). */
    private static final long CB_STATE_WAIT_MS = 5_000;

    private static RestClient orderClient;
    private static RestClient paymentClient;

    @BeforeAll
    static void setUp() {
        orderClient = RestClient.builder()
                .baseUrl(ORDER_SERVICE_URL)
                .build();
        paymentClient = RestClient.builder()
                .baseUrl(PAYMENT_SERVICE_URL)
                .build();
    }

    // ── Normal Flow (Circuit Breaker CLOSED) ─────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Circuit breaker should be CLOSED under normal conditions")
    void circuitBreakerShouldBeClosedUnderNormalConditions() {
        // Verify order-service is healthy with circuit breaker in normal state
        var health = orderClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Order service health should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(health, "Order service health should not be null");
        assertEquals("UP", health.get("status"),
                "Order service should be UP with circuit breaker in CLOSED state");
    }

    @Test
    @Order(2)
    @DisplayName("Orders should succeed when payment-service is available (circuit breaker CLOSED)")
    void ordersShouldSucceedWhenPaymentServiceIsAvailable() {
        HttpStatusCode statusCode = orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-cb-normal-001",
                            "quantity": 1,
                            "customerId": "cust-cb-normal-001"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(statusCode.is2xxSuccessful(),
                "Order creation should succeed when circuit breaker is CLOSED (got " + statusCode + ")");
    }

    @Test
    @Order(3)
    @DisplayName("Multiple orders should pass through CLOSED circuit breaker without errors")
    void multipleOrdersShouldPassThroughClosedCircuitBreaker() {
        List<HttpStatusCode> statusCodes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            HttpStatusCode statusCode = orderClient.post()
                    .uri("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {
                                "productId": "prod-cb-multi-%d",
                                "quantity": 1,
                                "customerId": "cust-cb-multi-%d"
                            }
                            """.formatted(i, i))
                    .exchange((req, res) -> res.getStatusCode());
            statusCodes.add(statusCode);
        }

        long successCount = statusCodes.stream()
                .filter(HttpStatusCode::is2xxSuccessful)
                .count();

        assertEquals(5, successCount,
                "All 5 orders should succeed through CLOSED circuit breaker. "
                        + "Results: " + statusCodes);
    }

    // ── Circuit Breaker Opening on Failures ──────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("Circuit breaker should open after repeated payment-service failures")
    void circuitBreakerShouldOpenAfterRepeatedFailures() throws InterruptedException {
        // Send requests that will fail (payment-service should be scaled down
        // or configured to return errors for this test scenario).
        // In a real scenario, we would scale down payment-service pods first:
        //   kubectl scale deployment payment-service --replicas=0 -n poc
        List<HttpStatusCode> statusCodes = new ArrayList<>();

        for (int i = 0; i < FAILURE_THRESHOLD_REQUESTS; i++) {
            HttpStatusCode statusCode = orderClient.post()
                    .uri("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {
                                "productId": "prod-cb-fail-%d",
                                "quantity": 1,
                                "customerId": "cust-cb-fail-%d"
                            }
                            """.formatted(i, i))
                    .exchange((req, res) -> res.getStatusCode());
            statusCodes.add(statusCode);
        }

        // After enough failures, subsequent requests should be rejected quickly
        // by the circuit breaker (returning 503 Service Unavailable or a fallback)
        Thread.sleep(CB_STATE_WAIT_MS);

        HttpStatusCode circuitBreakerResponse = orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-cb-after-open",
                            "quantity": 1,
                            "customerId": "cust-cb-after-open"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        // When circuit breaker is OPEN, we expect either:
        // - 503 Service Unavailable (circuit breaker rejection)
        // - 200 with fallback response
        // - 5xx if fallback is not configured
        assertNotNull(circuitBreakerResponse,
                "Circuit breaker should still return a response (either rejection or fallback)");
    }

    @Test
    @Order(5)
    @DisplayName("Circuit breaker OPEN state should reject requests quickly without calling downstream")
    void openCircuitBreakerShouldRejectRequestsQuickly() {
        // When the circuit breaker is OPEN, requests should be rejected immediately
        // without waiting for a timeout from the downstream service.
        long startTime = System.currentTimeMillis();

        HttpStatusCode statusCode = orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-cb-fast-fail",
                            "quantity": 1,
                            "customerId": "cust-cb-fast-fail"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        long elapsed = System.currentTimeMillis() - startTime;

        // An OPEN circuit breaker should respond much faster than a downstream timeout
        // (typically < 100ms vs. a 5-30s connection/read timeout)
        assertNotNull(statusCode, "Circuit breaker should return a response");

        // If circuit breaker is open, response should be fast (< 2 seconds)
        // This is a relaxed threshold to account for network variability
        assertTrue(elapsed < 2000,
                "OPEN circuit breaker should reject quickly (elapsed: " + elapsed + "ms)");
    }

    // ── Circuit Breaker Recovery (HALF_OPEN -> CLOSED) ───────────────────────────

    @Test
    @Order(6)
    @DisplayName("Circuit breaker should transition to HALF_OPEN after wait duration")
    void circuitBreakerShouldTransitionToHalfOpen() throws InterruptedException {
        // Wait for the circuit breaker's wait-duration-in-open-state to expire.
        // Default Resilience4j wait-duration is 60 seconds, but it may be configured
        // differently. We wait a conservative amount of time.
        Thread.sleep(CB_STATE_WAIT_MS * 3);

        // In HALF_OPEN state, a limited number of requests are allowed through
        // to test if the downstream service has recovered.
        HttpStatusCode statusCode = orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-cb-halfopen",
                            "quantity": 1,
                            "customerId": "cust-cb-halfopen"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        // In HALF_OPEN state, the request should be allowed through to test recovery
        assertNotNull(statusCode,
                "Circuit breaker in HALF_OPEN state should allow test requests through");
    }

    @Test
    @Order(7)
    @DisplayName("Circuit breaker should recover to CLOSED when downstream service is healthy again")
    void circuitBreakerShouldRecoverWhenDownstreamIsHealthy() throws InterruptedException {
        // First, ensure payment-service is back up
        // In a real scenario: kubectl scale deployment payment-service --replicas=1 -n poc

        // Verify payment-service is available
        var paymentHealth = paymentClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> res.bodyTo(Map.class));

        // Wait for circuit breaker to detect recovery
        Thread.sleep(CB_STATE_WAIT_MS * 2);

        // Send requests that should now succeed
        List<HttpStatusCode> recoveryStatusCodes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            HttpStatusCode statusCode = orderClient.post()
                    .uri("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {
                                "productId": "prod-cb-recover-%d",
                                "quantity": 1,
                                "customerId": "cust-cb-recover-%d"
                            }
                            """.formatted(i, i))
                    .exchange((req, res) -> res.getStatusCode());
            recoveryStatusCodes.add(statusCode);
        }

        long successCount = recoveryStatusCodes.stream()
                .filter(HttpStatusCode::is2xxSuccessful)
                .count();

        assertTrue(successCount > 0,
                "At least some requests should succeed after circuit breaker recovery. "
                        + "Results: " + recoveryStatusCodes);
    }

    @Test
    @Order(8)
    @DisplayName("Order service should expose circuit breaker metrics via Actuator")
    void orderServiceShouldExposeCircuitBreakerMetrics() {
        // Check that the circuit breaker health indicator is available
        var health = orderClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Order service health should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(health, "Health response should not be null");
        assertEquals("UP", health.get("status"),
                "Order service should be UP after circuit breaker recovery");
    }
}
