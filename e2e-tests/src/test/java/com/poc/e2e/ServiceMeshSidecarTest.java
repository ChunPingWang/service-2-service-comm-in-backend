package com.poc.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Istio sidecar injection verification tests.
 *
 * <p>Validates that every service pod in the "poc" namespace has the
 * {@code istio-proxy} sidecar container injected. The namespace label
 * {@code istio-injection: enabled} must be present in
 * {@code infrastructure/k8s/namespaces.yaml} for automatic injection.</p>
 *
 * <p>These tests call the Kubernetes API (via kubectl proxy at localhost:8001)
 * to inspect pod container lists. They require a running Kind cluster with
 * Istio installed and all services deployed.</p>
 */
@SpringBootTest(classes = ServiceMeshSidecarTest.class)
@Disabled("Requires Kind cluster with Istio installed")
class ServiceMeshSidecarTest {

    private static final String K8S_API_URL = "http://localhost:8001";
    private static final String NAMESPACE = "poc";

    private static RestClient k8sClient;

    @BeforeAll
    static void setUp() {
        k8sClient = RestClient.builder()
                .baseUrl(K8S_API_URL)
                .build();
    }

    @ParameterizedTest(name = "{0} pod should have istio-proxy sidecar container")
    @CsvSource({
            "order-service",
            "product-service",
            "payment-service",
            "notification-service",
            "shipping-service"
    })
    @DisplayName("Service pod should have istio-proxy sidecar container")
    void servicePodShouldHaveIstioSidecar(String serviceName) {
        // Query the Kubernetes API for pods matching the service label
        var podsResponse = k8sClient.get()
                .uri("/api/v1/namespaces/{ns}/pods?labelSelector=app={app}",
                        NAMESPACE, serviceName)
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Kubernetes API should return 200 for pod list");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(podsResponse, "Kubernetes API should return pods data for " + serviceName);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) podsResponse.get("items");
        assertNotNull(items, "Pod items list should not be null for " + serviceName);
        assertFalse(items.isEmpty(),
                "Should have at least one pod for " + serviceName);

        // Check each pod for the istio-proxy container
        for (Map<String, Object> pod : items) {
            @SuppressWarnings("unchecked")
            Map<String, Object> spec = (Map<String, Object>) pod.get("spec");
            assertNotNull(spec, "Pod spec should not be null");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> containers =
                    (List<Map<String, Object>>) spec.get("containers");
            assertNotNull(containers, "Pod containers list should not be null");

            boolean hasIstioProxy = containers.stream()
                    .anyMatch(c -> "istio-proxy".equals(c.get("name")));

            assertTrue(hasIstioProxy,
                    serviceName + " pod should contain istio-proxy sidecar container. "
                            + "Found containers: "
                            + containers.stream().map(c -> (String) c.get("name")).toList());
        }
    }

    @ParameterizedTest(name = "{0} pod istio-proxy container should be running")
    @CsvSource({
            "order-service",
            "product-service",
            "payment-service",
            "notification-service",
            "shipping-service"
    })
    @DisplayName("Service pod istio-proxy sidecar should be in Running state")
    void istioSidecarShouldBeRunning(String serviceName) {
        var podsResponse = k8sClient.get()
                .uri("/api/v1/namespaces/{ns}/pods?labelSelector=app={app}",
                        NAMESPACE, serviceName)
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Kubernetes API should return 200 for pod list");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(podsResponse, "Kubernetes API should return pods data");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) podsResponse.get("items");
        assertFalse(items.isEmpty(), "Should have at least one pod for " + serviceName);

        for (Map<String, Object> pod : items) {
            @SuppressWarnings("unchecked")
            Map<String, Object> status = (Map<String, Object>) pod.get("status");
            assertNotNull(status, "Pod status should not be null");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> containerStatuses =
                    (List<Map<String, Object>>) status.get("containerStatuses");
            assertNotNull(containerStatuses, "Container statuses should not be null");

            var istioProxyStatus = containerStatuses.stream()
                    .filter(cs -> "istio-proxy".equals(cs.get("name")))
                    .findFirst();

            assertTrue(istioProxyStatus.isPresent(),
                    "Should have status for istio-proxy container in " + serviceName);

            Boolean ready = (Boolean) istioProxyStatus.get().get("ready");
            assertTrue(ready,
                    "istio-proxy sidecar in " + serviceName + " should be ready");
        }
    }

    @ParameterizedTest(name = "{0} pod should have exactly 2 containers (app + sidecar)")
    @CsvSource({
            "order-service",
            "product-service",
            "payment-service",
            "notification-service",
            "shipping-service"
    })
    @DisplayName("Service pod should have exactly 2 containers (application + istio-proxy)")
    void servicePodShouldHaveTwoContainers(String serviceName) {
        var podsResponse = k8sClient.get()
                .uri("/api/v1/namespaces/{ns}/pods?labelSelector=app={app}",
                        NAMESPACE, serviceName)
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Kubernetes API should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(podsResponse, "Should get pods data");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) podsResponse.get("items");
        assertFalse(items.isEmpty(), "Should have at least one pod for " + serviceName);

        for (Map<String, Object> pod : items) {
            @SuppressWarnings("unchecked")
            Map<String, Object> spec = (Map<String, Object>) pod.get("spec");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> containers =
                    (List<Map<String, Object>>) spec.get("containers");

            assertEquals(2, containers.size(),
                    serviceName + " pod should have exactly 2 containers "
                            + "(application + istio-proxy), but found: "
                            + containers.stream().map(c -> (String) c.get("name")).toList());
        }
    }
}
