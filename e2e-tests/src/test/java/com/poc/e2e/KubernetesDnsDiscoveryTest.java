package com.poc.e2e;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kubernetes DNS service discovery tests.
 *
 * <p>Verifies that each microservice can be resolved and reached via
 * Kubernetes DNS names following the pattern
 * {@code <service-name>.poc.svc.cluster.local:<port>}.</p>
 *
 * <p>K8s DNS is the primary (zero-code) service discovery mechanism.
 * No additional libraries or configuration are needed beyond the
 * standard K8s Service resources already deployed.</p>
 *
 * <p>Services under test:
 * <ul>
 *   <li>order-service (port 8081)</li>
 *   <li>product-service (port 8082)</li>
 *   <li>payment-service (port 8083)</li>
 *   <li>notification-service (port 8084)</li>
 *   <li>shipping-service (port 8085)</li>
 * </ul>
 */
@Disabled("Requires Kind cluster")
class KubernetesDnsDiscoveryTest {

    private static final String DNS_SUFFIX = ".poc.svc.cluster.local";

    private final RestClient restClient = RestClient.create();

    // ── DNS Resolution Tests ─────────────────────────────────────────────────────

    @ParameterizedTest(name = "DNS resolution: {0}")
    @CsvSource({
            "order-service",
            "product-service",
            "payment-service",
            "notification-service",
            "shipping-service"
    })
    @DisplayName("K8s DNS should resolve service FQDN to an IP address")
    void k8sDnsShouldResolveServiceFqdn(String serviceName) {
        String fqdn = serviceName + DNS_SUFFIX;

        InetAddress address = assertDoesNotThrow(
                () -> InetAddress.getByName(fqdn),
                "DNS resolution should succeed for " + fqdn
        );

        assertNotNull(address, "Resolved address for " + fqdn + " should not be null");
        assertNotNull(address.getHostAddress(),
                "Resolved IP for " + fqdn + " should not be null");
    }

    @ParameterizedTest(name = "Short DNS resolution: {0}")
    @CsvSource({
            "order-service",
            "product-service",
            "payment-service",
            "notification-service",
            "shipping-service"
    })
    @DisplayName("K8s DNS should resolve short service names within the same namespace")
    void k8sDnsShouldResolveShortServiceName(String serviceName) {
        InetAddress address = assertDoesNotThrow(
                () -> InetAddress.getByName(serviceName),
                "Short DNS name resolution should succeed for " + serviceName
        );

        assertNotNull(address.getHostAddress(),
                "Resolved IP for " + serviceName + " should not be null");
    }

    // ── HTTP Health Check Tests via DNS ──────────────────────────────────────────

    @ParameterizedTest(name = "HTTP health via DNS: {0}:{1}")
    @CsvSource({
            "order-service,    8081",
            "product-service,  8082",
            "payment-service,  8083",
            "notification-service, 8084",
            "shipping-service, 8085"
    })
    @DisplayName("HTTP call via K8s DNS FQDN should reach the actuator health endpoint")
    void httpCallViaK8sDnsFqdnShouldSucceed(String serviceName, int port) {
        String url = "http://" + serviceName + DNS_SUFFIX + ":" + port + "/actuator/health";

        String body = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);

        assertNotNull(body, "Health response from " + serviceName + " should not be null");
        assertTrue(body.contains("UP"),
                "Health response from " + serviceName + " should contain UP status");
    }

    @ParameterizedTest(name = "HTTP health via short name: {0}:{1}")
    @CsvSource({
            "order-service,    8081",
            "product-service,  8082",
            "payment-service,  8083",
            "notification-service, 8084",
            "shipping-service, 8085"
    })
    @DisplayName("HTTP call via short K8s DNS name should reach the actuator health endpoint")
    void httpCallViaShortDnsNameShouldSucceed(String serviceName, int port) {
        String url = "http://" + serviceName + ":" + port + "/actuator/health";

        String body = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);

        assertNotNull(body, "Health response from " + serviceName + " should not be null");
        assertTrue(body.contains("UP"),
                "Health response from " + serviceName + " should contain UP status");
    }

    // ── Cross-Service Communication via DNS ──────────────────────────────────────

    @Test
    @DisplayName("Order service should reach payment service via K8s DNS")
    void orderServiceShouldReachPaymentServiceViaDns() {
        // Verify that the order-service can call payment-service via K8s DNS,
        // simulated here by calling payment-service health from the test pod
        String paymentUrl = "http://payment-service" + DNS_SUFFIX + ":8083/actuator/health";

        String body = restClient.get()
                .uri(paymentUrl)
                .retrieve()
                .body(String.class);

        assertNotNull(body, "Payment service should be reachable via K8s DNS");
        assertTrue(body.contains("UP"), "Payment service should report UP status");
    }

    @Test
    @DisplayName("FQDN and short DNS name should resolve to the same IP")
    void fqdnAndShortNameShouldResolveToSameIp() throws UnknownHostException {
        String serviceName = "order-service";

        InetAddress fqdnAddress = InetAddress.getByName(serviceName + DNS_SUFFIX);
        InetAddress shortAddress = InetAddress.getByName(serviceName);

        assertEquals(fqdnAddress.getHostAddress(), shortAddress.getHostAddress(),
                "FQDN and short name should resolve to the same ClusterIP");
    }
}
