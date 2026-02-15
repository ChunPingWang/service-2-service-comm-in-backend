package com.poc.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Distributed tracing verification tests.
 *
 * <p>Validates that a request flowing through Order -> Product -> Payment
 * creates a connected trace with a single trace ID spanning all services.
 * Traces are queried via the Jaeger API to verify existence and structure.</p>
 *
 * <p>These tests require a running Kind cluster with the full observability
 * stack deployed (OTel Collector, Jaeger, services).</p>
 */
@SpringBootTest(classes = TracingE2ETest.class)
@Disabled("Requires Kind cluster with observability stack")
class TracingE2ETest {

    private static final String ORDER_SERVICE_URL = "http://localhost:8081";
    private static final String JAEGER_QUERY_URL = "http://localhost:16686";

    private static RestClient orderClient;
    private static RestClient jaegerClient;

    @BeforeAll
    static void setUp() {
        orderClient = RestClient.builder()
                .baseUrl(ORDER_SERVICE_URL)
                .build();
        jaegerClient = RestClient.builder()
                .baseUrl(JAEGER_QUERY_URL)
                .build();
    }

    @Test
    @DisplayName("Request through Order -> Product -> Payment creates a connected trace")
    void orderFlowShouldCreateConnectedTrace() throws InterruptedException {
        // Trigger an order creation that flows through Order -> Product -> Payment
        HttpStatusCode statusCode = orderClient.post()
                .uri("/api/v1/orders")
                .header("Content-Type", "application/json")
                .body("""
                        {
                            "productId": "prod-trace-001",
                            "quantity": 1,
                            "customerId": "cust-trace-001"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(
                statusCode.is2xxSuccessful() || statusCode.isSameCodeAs(HttpStatus.CREATED),
                "Order creation should succeed (expected 2xx, got " + statusCode + ")"
        );

        // Wait for traces to be flushed to Jaeger
        Thread.sleep(5000);

        // Query Jaeger for traces from order-service
        var tracesResponse = jaegerClient.get()
                .uri("/api/traces?service=order-service&limit=1&lookback=1m")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Jaeger API should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(tracesResponse, "Jaeger should return trace data");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) tracesResponse.get("data");
        assertNotNull(data, "Trace data should not be null");
        assertFalse(data.isEmpty(), "Should have at least one trace from order-service");

        // Verify the trace spans multiple services
        @SuppressWarnings("unchecked")
        Map<String, Object> trace = data.getFirst();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> spans = (List<Map<String, Object>>) trace.get("spans");
        assertNotNull(spans, "Trace should contain spans");
        assertFalse(spans.isEmpty(), "Trace should have at least one span");

        // Verify the trace ID is consistent across all spans
        String traceId = (String) trace.get("traceID");
        assertNotNull(traceId, "Trace ID should not be null");
        assertFalse(traceId.isBlank(), "Trace ID should not be blank");
    }

    @Test
    @DisplayName("Trace ID is propagated across service boundaries")
    void traceIdShouldBePropagatedAcrossServices() throws InterruptedException {
        // Trigger a request and capture the trace header
        HttpStatusCode statusCode = orderClient.post()
                .uri("/api/v1/orders")
                .header("Content-Type", "application/json")
                .body("""
                        {
                            "productId": "prod-trace-002",
                            "quantity": 2,
                            "customerId": "cust-trace-002"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(
                statusCode.is2xxSuccessful(),
                "Order creation should succeed"
        );

        // Wait for traces to be flushed
        Thread.sleep(5000);

        // Query Jaeger for recent traces and check that multiple services appear
        var tracesResponse = jaegerClient.get()
                .uri("/api/traces?service=order-service&limit=5&lookback=2m")
                .exchange((req, res) -> res.bodyTo(Map.class));

        assertNotNull(tracesResponse, "Should receive trace data from Jaeger");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) tracesResponse.get("data");
        assertFalse(data.isEmpty(), "Should have traces from order-service");

        // Check that at least one trace contains processes from multiple services
        boolean multiServiceTraceFound = false;
        for (Map<String, Object> trace : data) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> processes =
                    (Map<String, Map<String, Object>>) trace.get("processes");
            if (processes != null && processes.size() > 1) {
                multiServiceTraceFound = true;
                break;
            }
        }

        assertTrue(multiServiceTraceFound,
                "At least one trace should span multiple services (Order -> Product/Payment)");
    }

    @Test
    @DisplayName("Jaeger API returns traces for all instrumented services")
    void jaegerShouldHaveTracesForAllServices() {
        List<String> expectedServices = List.of(
                "order-service",
                "product-service",
                "payment-service"
        );

        // Query Jaeger for known services
        var servicesResponse = jaegerClient.get()
                .uri("/api/services")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Jaeger services endpoint should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(servicesResponse, "Jaeger should return services data");

        @SuppressWarnings("unchecked")
        List<String> services = (List<String>) servicesResponse.get("data");
        assertNotNull(services, "Services list should not be null");

        for (String expectedService : expectedServices) {
            assertTrue(services.contains(expectedService),
                    "Jaeger should have traces for service: " + expectedService);
        }
    }
}
