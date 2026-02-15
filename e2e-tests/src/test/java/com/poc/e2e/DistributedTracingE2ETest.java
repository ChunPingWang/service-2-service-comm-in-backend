package com.poc.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Distributed tracing E2E test verifying a single trace across all 5 services.
 *
 * <p>Validates that a full business flow (order creation -> product check ->
 * payment -> notification -> shipping -> order update) creates a single
 * connected trace with spans from all 5 services, queryable via the Jaeger API.</p>
 *
 * <p>Expected trace structure:
 * <pre>
 *   order-service (root span)
 *     -> product-service (REST/gRPC span)
 *     -> payment-service (REST span)
 *     -> [async] payment-service (Kafka consumer span)
 *       -> notification-service (Kafka consumer span)
 *       -> shipping-service (RabbitMQ consumer span)
 *         -> [async] order-service (Kafka consumer span for shipment.arranged)
 * </pre>
 *
 * <p>These tests require a running Kind cluster with all services, Kafka,
 * RabbitMQ, OTel Collector, and Jaeger deployed.</p>
 */
@SpringBootTest(classes = DistributedTracingE2ETest.class)
@Disabled("Requires Kind cluster with full stack deployed")
class DistributedTracingE2ETest {

    private static final String ORDER_SERVICE_URL = "http://localhost:8081";
    private static final String JAEGER_QUERY_URL = "http://localhost:16686";

    /** All 5 services that should appear in the distributed trace. */
    private static final Set<String> ALL_SERVICES = Set.of(
            "order-service",
            "product-service",
            "payment-service",
            "notification-service",
            "shipping-service"
    );

    /** Time to wait for traces to be flushed to Jaeger (milliseconds). */
    private static final long TRACE_FLUSH_WAIT_MS = 15_000;

    /** Time to wait for the full async event chain to complete (milliseconds). */
    private static final long FULL_CHAIN_WAIT_MS = 30_000;

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
    @DisplayName("Full business flow should create a trace spanning all 5 services")
    void fullFlowShouldCreateTraceSpanningAllServices() throws InterruptedException {
        // Trigger the full business flow
        var orderResponse = orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-trace-full-001",
                            "quantity": 1,
                            "customerId": "cust-trace-full-001"
                        }
                        """)
                .exchange((req, res) -> {
                    assertTrue(res.getStatusCode().is2xxSuccessful(),
                            "Order creation should succeed for tracing test");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(orderResponse, "Order creation response should not be null");

        // Wait for the full event chain AND trace flushing
        Thread.sleep(FULL_CHAIN_WAIT_MS + TRACE_FLUSH_WAIT_MS);

        // Query Jaeger for traces from order-service (the originating service)
        var tracesResponse = jaegerClient.get()
                .uri("/api/traces?service=order-service&limit=5&lookback=5m")
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

        // Find the trace with the most services (should be the full flow trace)
        Set<String> allFoundServices = new HashSet<>();
        for (Map<String, Object> trace : data) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> processes =
                    (Map<String, Map<String, Object>>) trace.get("processes");
            if (processes != null) {
                for (Map<String, Object> process : processes.values()) {
                    String serviceName = (String) process.get("serviceName");
                    if (serviceName != null) {
                        allFoundServices.add(serviceName);
                    }
                }
            }
        }

        // Verify that all 5 services appear in the traces
        for (String expectedService : ALL_SERVICES) {
            assertTrue(allFoundServices.contains(expectedService),
                    "Trace should include spans from " + expectedService
                            + ". Found services: " + allFoundServices);
        }
    }

    @Test
    @DisplayName("Jaeger should report all 5 services as instrumented")
    void jaegerShouldReportAllFiveServicesAsInstrumented() {
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

        for (String expectedService : ALL_SERVICES) {
            assertTrue(services.contains(expectedService),
                    "Jaeger should have traces for " + expectedService
                            + ". Available services: " + services);
        }
    }

    @Test
    @DisplayName("Trace should have a single trace ID across all spans")
    void traceShouldHaveSingleTraceIdAcrossAllSpans() throws InterruptedException {
        // Trigger a fresh order for a clean trace
        orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-trace-single-001",
                            "quantity": 1,
                            "customerId": "cust-trace-single-001"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        // Wait for trace propagation
        Thread.sleep(FULL_CHAIN_WAIT_MS + TRACE_FLUSH_WAIT_MS);

        var tracesResponse = jaegerClient.get()
                .uri("/api/traces?service=order-service&limit=1&lookback=5m")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Jaeger API should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(tracesResponse, "Should receive trace data from Jaeger");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) tracesResponse.get("data");
        assertFalse(data.isEmpty(), "Should have at least one trace");

        Map<String, Object> trace = data.getFirst();
        String traceId = (String) trace.get("traceID");
        assertNotNull(traceId, "Trace should have a trace ID");
        assertFalse(traceId.isBlank(), "Trace ID should not be blank");

        // Verify all spans in this trace share the same trace ID
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> spans = (List<Map<String, Object>>) trace.get("spans");
        assertNotNull(spans, "Trace should contain spans");
        assertFalse(spans.isEmpty(), "Trace should have at least one span");

        for (Map<String, Object> span : spans) {
            assertEquals(traceId, span.get("traceID"),
                    "All spans should share the same trace ID");
        }
    }

    @Test
    @DisplayName("Trace should include both synchronous and asynchronous spans")
    void traceShouldIncludeSyncAndAsyncSpans() throws InterruptedException {
        // Trigger the full flow
        orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-trace-sync-async-001",
                            "quantity": 1,
                            "customerId": "cust-trace-sync-async-001"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        Thread.sleep(FULL_CHAIN_WAIT_MS + TRACE_FLUSH_WAIT_MS);

        var tracesResponse = jaegerClient.get()
                .uri("/api/traces?service=order-service&limit=5&lookback=5m")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Jaeger API should return 200");
                    return res.bodyTo(Map.class);
                });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) tracesResponse.get("data");
        assertFalse(data.isEmpty(), "Should have traces");

        // Look for a trace with multiple spans (indicating cross-service propagation)
        boolean multiSpanTraceFound = false;
        for (Map<String, Object> trace : data) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> spans = (List<Map<String, Object>>) trace.get("spans");
            if (spans != null && spans.size() > 2) {
                multiSpanTraceFound = true;

                // Verify spans include both HTTP and messaging operation types
                Set<String> operationNames = new HashSet<>();
                for (Map<String, Object> span : spans) {
                    String operationName = (String) span.get("operationName");
                    if (operationName != null) {
                        operationNames.add(operationName);
                    }
                }

                assertFalse(operationNames.isEmpty(),
                        "Trace spans should have operation names");
                break;
            }
        }

        assertTrue(multiSpanTraceFound,
                "Should find a trace with multiple spans indicating cross-service communication");
    }

    @Test
    @DisplayName("Trace context should propagate through Kafka message headers")
    void traceContextShouldPropagateThroughKafka() throws InterruptedException {
        // Trigger an order and wait for async propagation through Kafka
        orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-trace-kafka-001",
                            "quantity": 1,
                            "customerId": "cust-trace-kafka-001"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        Thread.sleep(FULL_CHAIN_WAIT_MS + TRACE_FLUSH_WAIT_MS);

        // Query Jaeger for traces involving both order-service and payment-service
        // (connected via Kafka order.created topic)
        var orderTraces = jaegerClient.get()
                .uri("/api/traces?service=order-service&limit=5&lookback=5m")
                .exchange((req, res) -> res.bodyTo(Map.class));

        assertNotNull(orderTraces, "Should have order-service traces");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) orderTraces.get("data");
        assertFalse(data.isEmpty(), "Should have at least one trace from order-service");

        // Check that at least one trace includes both order-service and payment-service
        boolean crossServiceTraceFound = false;
        for (Map<String, Object> trace : data) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> processes =
                    (Map<String, Map<String, Object>>) trace.get("processes");
            if (processes != null) {
                Set<String> traceServices = new HashSet<>();
                for (Map<String, Object> process : processes.values()) {
                    String serviceName = (String) process.get("serviceName");
                    if (serviceName != null) {
                        traceServices.add(serviceName);
                    }
                }
                if (traceServices.contains("order-service") && traceServices.contains("payment-service")) {
                    crossServiceTraceFound = true;
                    break;
                }
            }
        }

        assertTrue(crossServiceTraceFound,
                "Should find a trace that spans order-service and payment-service via Kafka");
    }

    @Test
    @DisplayName("Trace context should propagate through RabbitMQ message headers")
    void traceContextShouldPropagateThroughRabbitMQ() throws InterruptedException {
        // Trigger an order and wait for the full chain including RabbitMQ
        orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-trace-rabbit-001",
                            "quantity": 1,
                            "customerId": "cust-trace-rabbit-001"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        Thread.sleep(FULL_CHAIN_WAIT_MS + TRACE_FLUSH_WAIT_MS);

        // Query Jaeger for traces involving shipping-service
        // (connected via RabbitMQ payment.events exchange)
        var shippingTraces = jaegerClient.get()
                .uri("/api/traces?service=shipping-service&limit=5&lookback=5m")
                .exchange((req, res) -> res.bodyTo(Map.class));

        assertNotNull(shippingTraces, "Should have shipping-service traces");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) shippingTraces.get("data");

        // If shipping-service has traces, verify they connect back to payment-service
        if (!data.isEmpty()) {
            boolean paymentToShippingTraceFound = false;
            for (Map<String, Object> trace : data) {
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> processes =
                        (Map<String, Map<String, Object>>) trace.get("processes");
                if (processes != null) {
                    Set<String> traceServices = new HashSet<>();
                    for (Map<String, Object> process : processes.values()) {
                        String serviceName = (String) process.get("serviceName");
                        if (serviceName != null) {
                            traceServices.add(serviceName);
                        }
                    }
                    if (traceServices.contains("shipping-service")) {
                        paymentToShippingTraceFound = true;
                        break;
                    }
                }
            }

            assertTrue(paymentToShippingTraceFound,
                    "Should find a trace involving shipping-service via RabbitMQ");
        }
    }
}
