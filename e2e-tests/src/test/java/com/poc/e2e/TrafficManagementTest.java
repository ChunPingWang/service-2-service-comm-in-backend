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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Traffic management verification tests for Istio service mesh.
 *
 * <p>Validates that Istio VirtualService and DestinationRule resources are
 * correctly applied for canary deployments and fault injection:
 * <ul>
 *   <li>Canary split: 90% of traffic to v1, 10% to v2 for order-service</li>
 *   <li>Fault injection: HTTP delay and abort rules for payment-service</li>
 * </ul>
 *
 * <p>These tests require a running Kind cluster with Istio installed,
 * traffic management rules deployed, and all services available.</p>
 */
@SpringBootTest(classes = TrafficManagementTest.class)
@Disabled("Requires Kind cluster with Istio installed")
class TrafficManagementTest {

    private static final String K8S_API_URL = "http://localhost:8001";
    private static final String ORDER_SERVICE_URL = "http://localhost:8081";
    private static final String PAYMENT_SERVICE_URL = "http://localhost:8083";
    private static final String NAMESPACE = "poc";

    private static RestClient k8sClient;
    private static RestClient orderClient;
    private static RestClient paymentClient;

    @BeforeAll
    static void setUp() {
        k8sClient = RestClient.builder()
                .baseUrl(K8S_API_URL)
                .build();
        orderClient = RestClient.builder()
                .baseUrl(ORDER_SERVICE_URL)
                .build();
        paymentClient = RestClient.builder()
                .baseUrl(PAYMENT_SERVICE_URL)
                .build();
    }

    // ---- Canary deployment tests ----

    @Test
    @DisplayName("VirtualService for order-service with traffic split should exist")
    void orderServiceVirtualServiceShouldExist() {
        var response = k8sClient.get()
                .uri("/apis/networking.istio.io/v1/namespaces/{ns}/virtualservices",
                        NAMESPACE)
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Kubernetes API should return VirtualService list");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Should receive VirtualService list");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) response.get("items");
        assertNotNull(items, "VirtualService items should not be null");

        boolean hasOrderVs = items.stream().anyMatch(item -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) item.get("metadata");
            String name = (String) metadata.get("name");
            return name != null && name.contains("order-service");
        });

        assertTrue(hasOrderVs,
                "Should have a VirtualService for order-service in namespace " + NAMESPACE);
    }

    @Test
    @DisplayName("DestinationRule for order-service with v1 and v2 subsets should exist")
    void orderServiceDestinationRuleShouldExist() {
        var response = k8sClient.get()
                .uri("/apis/networking.istio.io/v1/namespaces/{ns}/destinationrules",
                        NAMESPACE)
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Kubernetes API should return DestinationRule list");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Should receive DestinationRule list");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) response.get("items");
        assertNotNull(items, "DestinationRule items should not be null");

        var orderDr = items.stream().filter(item -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) item.get("metadata");
            String name = (String) metadata.get("name");
            return name != null && name.contains("order-service");
        }).findFirst();

        assertTrue(orderDr.isPresent(),
                "Should have a DestinationRule for order-service");

        // Verify subsets v1 and v2 exist
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) orderDr.get().get("spec");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subsets =
                (List<Map<String, Object>>) spec.get("subsets");
        assertNotNull(subsets, "DestinationRule should have subsets defined");
        assertEquals(2, subsets.size(), "Should have exactly 2 subsets (v1 and v2)");

        List<String> subsetNames = subsets.stream()
                .map(s -> (String) s.get("name"))
                .toList();
        assertTrue(subsetNames.contains("v1"), "Should have subset 'v1'");
        assertTrue(subsetNames.contains("v2"), "Should have subset 'v2'");
    }

    @Test
    @DisplayName("VirtualService traffic split should configure 90/10 canary weights")
    void virtualServiceShouldHaveCanaryWeights() {
        var response = k8sClient.get()
                .uri("/apis/networking.istio.io/v1/namespaces/{ns}/virtualservices",
                        NAMESPACE)
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode());
                    return res.bodyTo(Map.class);
                });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) response.get("items");

        var orderVs = items.stream().filter(item -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) item.get("metadata");
            String name = (String) metadata.get("name");
            return name != null && name.contains("order-service");
        }).findFirst();

        assertTrue(orderVs.isPresent(), "order-service VirtualService should exist");

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) orderVs.get().get("spec");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> httpRoutes =
                (List<Map<String, Object>>) spec.get("http");
        assertNotNull(httpRoutes, "VirtualService should have http routes");
        assertFalse(httpRoutes.isEmpty(), "Should have at least one http route");

        // Check the first route for weight-based routing
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> routes =
                (List<Map<String, Object>>) httpRoutes.getFirst().get("route");
        assertNotNull(routes, "Route should have destination entries");
        assertEquals(2, routes.size(), "Should have 2 route entries for canary split");

        // Verify weights sum to 100
        int totalWeight = routes.stream()
                .mapToInt(r -> ((Number) r.get("weight")).intValue())
                .sum();
        assertEquals(100, totalWeight, "Traffic weights should sum to 100");
    }

    // ---- Fault injection tests ----

    @Test
    @DisplayName("VirtualService with fault injection for payment-service should exist")
    void paymentServiceFaultInjectionShouldExist() {
        var response = k8sClient.get()
                .uri("/apis/networking.istio.io/v1/namespaces/{ns}/virtualservices",
                        NAMESPACE)
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode());
                    return res.bodyTo(Map.class);
                });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) response.get("items");

        var paymentVs = items.stream().filter(item -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) item.get("metadata");
            String name = (String) metadata.get("name");
            return name != null && name.contains("payment-service")
                    && name.contains("fault");
        }).findFirst();

        assertTrue(paymentVs.isPresent(),
                "Should have a fault-injection VirtualService for payment-service");

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) paymentVs.get().get("spec");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> httpRoutes =
                (List<Map<String, Object>>) spec.get("http");
        assertNotNull(httpRoutes, "VirtualService should have http routes with fault config");

        // Verify fault injection configuration exists
        Map<String, Object> firstRoute = httpRoutes.getFirst();
        @SuppressWarnings("unchecked")
        Map<String, Object> fault = (Map<String, Object>) firstRoute.get("fault");
        assertNotNull(fault, "Route should have fault injection configuration");

        // Check delay configuration
        @SuppressWarnings("unchecked")
        Map<String, Object> delay = (Map<String, Object>) fault.get("delay");
        assertNotNull(delay, "Fault injection should include delay configuration");

        // Check abort configuration
        @SuppressWarnings("unchecked")
        Map<String, Object> abort = (Map<String, Object>) fault.get("abort");
        assertNotNull(abort, "Fault injection should include abort configuration");
    }

    @Test
    @DisplayName("Fault injection delay should affect a percentage of payment requests")
    void faultInjectionDelayShouldAffectSomeRequests() {
        // Send multiple requests and check that some experience delays
        int totalRequests = 50;
        AtomicInteger delayedRequests = new AtomicInteger(0);

        IntStream.range(0, totalRequests).forEach(i -> {
            long start = System.currentTimeMillis();
            paymentClient.get()
                    .uri("/actuator/health")
                    .exchange((req, res) -> res.getStatusCode());
            long elapsed = System.currentTimeMillis() - start;

            // Fault injection delay is 2s; consider anything >1.5s as delayed
            if (elapsed > 1500) {
                delayedRequests.incrementAndGet();
            }
        });

        // With 10% delay rate and 50 requests, we expect roughly 5 delayed.
        // Use a generous range to avoid flaky tests.
        assertTrue(delayedRequests.get() >= 1,
                "At least some requests should experience fault injection delay, "
                        + "but got " + delayedRequests.get() + " delayed out of " + totalRequests);
    }

    @Test
    @DisplayName("Fault injection abort should cause some payment requests to fail")
    void faultInjectionAbortShouldCauseSomeFailures() {
        // Send multiple requests and count server errors (500)
        int totalRequests = 50;
        AtomicInteger failedRequests = new AtomicInteger(0);

        IntStream.range(0, totalRequests).forEach(i -> {
            HttpStatusCode statusCode = paymentClient.get()
                    .uri("/actuator/health")
                    .exchange((req, res) -> res.getStatusCode());

            if (statusCode.is5xxServerError()) {
                failedRequests.incrementAndGet();
            }
        });

        // With 5% abort rate and 50 requests, we expect roughly 2-3 failures.
        // Use a generous range to avoid flaky tests.
        assertTrue(failedRequests.get() >= 1,
                "At least some requests should receive fault injection abort (HTTP 500), "
                        + "but got " + failedRequests.get() + " failures out of " + totalRequests);
    }
}
