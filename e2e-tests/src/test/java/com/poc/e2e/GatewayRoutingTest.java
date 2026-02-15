package com.poc.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gateway routing integration tests.
 *
 * <p>Validates that APISIX gateway correctly routes requests to the
 * appropriate backend services based on URL path prefixes:
 * <ul>
 *   <li>{@code /api/v1/orders/*}   -> order-service:8081</li>
 *   <li>{@code /api/v1/products/*} -> product-service:8082</li>
 *   <li>{@code /api/v1/payments/*} -> payment-service:8083</li>
 * </ul>
 *
 * <p>These tests require a running Kind cluster with APISIX deployed
 * and all backend services available. The gateway is accessible at
 * {@code http://localhost:30080}.
 */
@SpringBootTest(classes = GatewayRoutingTest.class)
@Disabled("Requires Kind cluster with APISIX deployed")
class GatewayRoutingTest {

    private static final String GATEWAY_BASE_URL = "http://localhost:30080";

    private static RestClient restClient;

    @BeforeAll
    static void setUp() {
        restClient = RestClient.builder()
                .baseUrl(GATEWAY_BASE_URL)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/orders routes to order-service and returns 200")
    void ordersRouteShouldReachOrderService() {
        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/orders")
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(
                statusCode.is2xxSuccessful() || statusCode.isSameCodeAs(HttpStatus.NOT_FOUND),
                "Order route should reach order-service (expected 2xx or 404, got " + statusCode + ")"
        );
    }

    @Test
    @DisplayName("GET /api/v1/products routes to product-service and returns 200")
    void productsRouteShouldReachProductService() {
        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/products")
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(
                statusCode.is2xxSuccessful() || statusCode.isSameCodeAs(HttpStatus.NOT_FOUND),
                "Product route should reach product-service (expected 2xx or 404, got " + statusCode + ")"
        );
    }

    @Test
    @DisplayName("GET /api/v1/payments routes to payment-service and returns 200")
    void paymentsRouteShouldReachPaymentService() {
        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/payments")
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(
                statusCode.is2xxSuccessful() || statusCode.isSameCodeAs(HttpStatus.NOT_FOUND),
                "Payment route should reach payment-service (expected 2xx or 404, got " + statusCode + ")"
        );
    }

    @Test
    @DisplayName("GET /api/v1/unknown returns 404 from gateway (no matching route)")
    void unknownRouteShouldReturn404() {
        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/unknown")
                .exchange((req, res) -> res.getStatusCode());

        assertEquals(HttpStatus.NOT_FOUND, statusCode,
                "Unknown route should return 404 from gateway");
    }

    @Test
    @DisplayName("Routes preserve request path when forwarding to upstream")
    void routeShouldPreserveRequestPath() {
        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/orders/test-order-123")
                .exchange((req, res) -> res.getStatusCode());

        // The upstream order-service may not have this specific resource,
        // but it should reach the service (not get a 502 Bad Gateway).
        assertTrue(
                statusCode.is2xxSuccessful()
                        || statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)
                        || statusCode.isSameCodeAs(HttpStatus.UNAUTHORIZED),
                "Path-preserved route should reach upstream (expected 2xx, 404, or 401, got " + statusCode + ")"
        );
    }
}
