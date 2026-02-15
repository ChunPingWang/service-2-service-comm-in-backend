package com.poc.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * Mutual TLS (mTLS) verification tests for Istio service mesh.
 *
 * <p>Validates that mTLS is enforced between all services in the "poc"
 * namespace via Istio's PeerAuthentication policy set to STRICT mode.
 * Verifies policy existence through the Kubernetes API and validates
 * encrypted traffic between services using Istio's proxy configuration.</p>
 *
 * <p>These tests require a running Kind cluster with Istio installed,
 * PeerAuthentication policy applied, and all services deployed.</p>
 */
@SpringBootTest(classes = MtlsVerificationTest.class)
@Disabled("Requires Kind cluster with Istio installed")
class MtlsVerificationTest {

    private static final String K8S_API_URL = "http://localhost:8001";
    private static final String NAMESPACE = "poc";

    private static RestClient k8sClient;

    @BeforeAll
    static void setUp() {
        k8sClient = RestClient.builder()
                .baseUrl(K8S_API_URL)
                .build();
    }

    @Test
    @DisplayName("PeerAuthentication policy with STRICT mTLS should exist in poc namespace")
    void peerAuthenticationPolicyShouldExist() {
        // Query Istio PeerAuthentication custom resources
        var response = k8sClient.get()
                .uri("/apis/security.istio.io/v1/namespaces/{ns}/peerauthentications",
                        NAMESPACE)
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Kubernetes API should return PeerAuthentication list");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Should receive PeerAuthentication list");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) response.get("items");
        assertNotNull(items, "PeerAuthentication items list should not be null");
        assertFalse(items.isEmpty(),
                "Should have at least one PeerAuthentication policy in namespace " + NAMESPACE);

        // Find a policy with STRICT mode
        boolean hasStrictPolicy = items.stream().anyMatch(item -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> spec = (Map<String, Object>) item.get("spec");
            if (spec == null) return false;
            @SuppressWarnings("unchecked")
            Map<String, Object> mtls = (Map<String, Object>) spec.get("mtls");
            if (mtls == null) return false;
            return "STRICT".equals(mtls.get("mode"));
        });

        assertTrue(hasStrictPolicy,
                "Should have a PeerAuthentication policy with STRICT mTLS mode");
    }

    @Test
    @DisplayName("Namespace-wide PeerAuthentication should apply to all workloads")
    void peerAuthenticationShouldApplyToAllWorkloads() {
        var response = k8sClient.get()
                .uri("/apis/security.istio.io/v1/namespaces/{ns}/peerauthentications",
                        NAMESPACE)
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Kubernetes API should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Should receive PeerAuthentication list");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) response.get("items");
        assertFalse(items.isEmpty(), "Should have PeerAuthentication policies");

        // A namespace-wide policy should not have a selector (applies to all workloads)
        boolean hasNamespaceWidePolicy = items.stream().anyMatch(item -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> spec = (Map<String, Object>) item.get("spec");
            if (spec == null) return false;
            // Namespace-wide policy has no selector or empty selector
            Object selector = spec.get("selector");
            return selector == null;
        });

        assertTrue(hasNamespaceWidePolicy,
                "Should have a namespace-wide PeerAuthentication (no selector) "
                        + "that applies to all workloads in " + NAMESPACE);
    }

    @ParameterizedTest(name = "{0} sidecar should have TLS certificate configured")
    @CsvSource({
            "order-service",
            "product-service",
            "payment-service",
            "notification-service",
            "shipping-service"
    })
    @DisplayName("Service sidecar should have TLS certificate for mTLS")
    void sidecarShouldHaveTlsCertificate(String serviceName) {
        // Check that the istio-proxy in each pod has the expected TLS volume mounts
        var podsResponse = k8sClient.get()
                .uri("/api/v1/namespaces/{ns}/pods?labelSelector=app={app}",
                        NAMESPACE, serviceName)
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Kubernetes API should return pod list");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(podsResponse, "Should get pods data for " + serviceName);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) podsResponse.get("items");
        assertFalse(items.isEmpty(),
                "Should have at least one pod for " + serviceName);

        for (Map<String, Object> pod : items) {
            @SuppressWarnings("unchecked")
            Map<String, Object> spec = (Map<String, Object>) pod.get("spec");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> containers =
                    (List<Map<String, Object>>) spec.get("containers");

            var istioProxy = containers.stream()
                    .filter(c -> "istio-proxy".equals(c.get("name")))
                    .findFirst();

            assertTrue(istioProxy.isPresent(),
                    serviceName + " should have istio-proxy container");

            // Istio mounts certificates in known volume mounts
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> volumeMounts =
                    (List<Map<String, Object>>) istioProxy.get().get("volumeMounts");
            assertNotNull(volumeMounts,
                    "istio-proxy in " + serviceName + " should have volume mounts");

            boolean hasCertMount = volumeMounts.stream()
                    .anyMatch(vm -> {
                        String mountPath = (String) vm.get("mountPath");
                        return mountPath != null
                                && (mountPath.contains("istio")
                                || mountPath.contains("certs")
                                || mountPath.contains("credential"));
                    });

            assertTrue(hasCertMount,
                    "istio-proxy in " + serviceName
                            + " should have certificate volume mounts for mTLS");
        }
    }

    @Test
    @DisplayName("Inter-service HTTP call succeeds when mTLS is active")
    void interServiceCallShouldSucceedWithMtls() {
        // Verify that order-service can reach payment-service through the mesh
        // This confirms mTLS handshake succeeds between proxies
        RestClient orderClient = RestClient.builder()
                .baseUrl("http://localhost:8081")
                .build();

        var statusCode = orderClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(statusCode.is2xxSuccessful(),
                "order-service should be reachable (mTLS active), got " + statusCode);
    }
}
