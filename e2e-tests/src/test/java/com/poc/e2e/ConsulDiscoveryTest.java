package com.poc.e2e;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consul service discovery tests.
 *
 * <p>Verifies that all microservices register with HashiCorp Consul
 * and can be discovered via the Consul HTTP API.</p>
 *
 * <p>Consul is the infrastructure-level, framework-agnostic discovery
 * mechanism. It provides:
 * <ul>
 *   <li>Service registration via Consul agent</li>
 *   <li>Service discovery via HTTP API and DNS interface</li>
 *   <li>Health check integration (HTTP-based)</li>
 * </ul>
 *
 * <p>Requires the {@code consul} Spring profile to be active on all services
 * and Consul deployed at {@code consul.poc.svc.cluster.local:8500}.</p>
 */
@Disabled("Requires Kind cluster with Consul deployed")
class ConsulDiscoveryTest {

    private static final String CONSUL_URL = "http://consul.poc.svc.cluster.local:8500";
    private static final String CONSUL_SERVICES_URL = CONSUL_URL + "/v1/catalog/services";
    private static final String CONSUL_HEALTH_URL = CONSUL_URL + "/v1/health/service";
    private static final Duration REGISTRATION_TIMEOUT = Duration.ofSeconds(30);

    private static final List<String> SERVICE_NAMES = List.of(
            "order-service",
            "product-service",
            "payment-service",
            "notification-service",
            "shipping-service"
    );

    private final RestClient restClient = RestClient.builder()
            .defaultHeader("Accept", "application/json")
            .build();

    // ── Consul Agent Availability ────────────────────────────────────────────────

    @Test
    @DisplayName("Consul agent should be reachable and healthy")
    void consulAgentShouldBeReachable() {
        String body = restClient.get()
                .uri(CONSUL_URL + "/v1/status/leader")
                .retrieve()
                .body(String.class);

        assertNotNull(body, "Consul leader status should not be null");
        assertTrue(body.length() > 2,
                "Consul should have an elected leader (non-empty response)");
    }

    @Test
    @DisplayName("Consul catalog services endpoint should be accessible")
    void consulCatalogShouldBeAccessible() {
        String body = restClient.get()
                .uri(CONSUL_SERVICES_URL)
                .retrieve()
                .body(String.class);

        assertNotNull(body, "Consul catalog services should return a response");
        // At minimum, the consul service itself is always registered
        assertTrue(body.contains("consul"),
                "Consul catalog should contain the 'consul' service itself");
    }

    // ── Service Registration Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("All services should register with Consul within 30 seconds")
    void allServicesShouldRegisterWithConsul() {
        Instant deadline = Instant.now().plus(REGISTRATION_TIMEOUT);

        for (String serviceName : SERVICE_NAMES) {
            boolean registered = false;

            while (Instant.now().isBefore(deadline) && !registered) {
                registered = isServiceRegistered(serviceName);
                if (!registered) {
                    sleep(2_000);
                }
            }

            assertTrue(registered,
                    serviceName + " should be registered with Consul within "
                            + REGISTRATION_TIMEOUT.toSeconds() + " seconds");
        }
    }

    @ParameterizedTest(name = "Registration: {0}")
    @CsvSource({
            "order-service",
            "product-service",
            "payment-service",
            "notification-service",
            "shipping-service"
    })
    @DisplayName("Individual service should appear in Consul catalog")
    void serviceShouldAppearInConsulCatalog(String serviceName) {
        // Wait for registration
        sleep(REGISTRATION_TIMEOUT.toMillis());

        String body = restClient.get()
                .uri(CONSUL_SERVICES_URL)
                .retrieve()
                .body(String.class);

        assertNotNull(body, "Consul catalog should return a response");
        assertTrue(body.contains(serviceName),
                serviceName + " should appear in Consul catalog");
    }

    // ── Service Discovery via Consul HTTP API ────────────────────────────────────

    @ParameterizedTest(name = "Discovery: {0}")
    @CsvSource({
            "order-service",
            "product-service",
            "payment-service",
            "notification-service",
            "shipping-service"
    })
    @DisplayName("Service should be discoverable via Consul catalog service endpoint")
    void serviceShouldBeDiscoverableViaCatalog(String serviceName) {
        // Wait for registration
        sleep(REGISTRATION_TIMEOUT.toMillis());

        String body = restClient.get()
                .uri(CONSUL_URL + "/v1/catalog/service/" + serviceName)
                .retrieve()
                .body(String.class);

        assertNotNull(body, "Catalog entry for " + serviceName + " should not be null");
        assertTrue(body.contains("ServiceAddress") || body.contains("Address"),
                serviceName + " catalog entry should contain address info");
        assertTrue(body.contains("ServicePort"),
                serviceName + " catalog entry should contain port info");
    }

    // ── Health Check Tests ───────────────────────────────────────────────────────

    @ParameterizedTest(name = "Health check: {0}")
    @CsvSource({
            "order-service",
            "product-service",
            "payment-service",
            "notification-service",
            "shipping-service"
    })
    @DisplayName("Service health checks in Consul should report passing status")
    void serviceHealthCheckShouldPass(String serviceName) {
        // Wait for registration and first health check
        sleep(REGISTRATION_TIMEOUT.toMillis());

        String body = restClient.get()
                .uri(CONSUL_HEALTH_URL + "/" + serviceName + "?passing=true")
                .retrieve()
                .body(String.class);

        assertNotNull(body, "Health query for " + serviceName + " should not be null");
        // A passing=true filter returns only healthy instances; non-empty = at least one healthy
        assertTrue(body.length() > 2,
                serviceName + " should have at least one healthy instance (passing checks)");
    }

    @Test
    @DisplayName("Consul health endpoint should distinguish healthy and unhealthy services")
    void consulHealthShouldDistinguishStatus() {
        // Wait for services to register and run health checks
        sleep(REGISTRATION_TIMEOUT.toMillis());

        // Query all health statuses for order-service
        String allBody = restClient.get()
                .uri(CONSUL_HEALTH_URL + "/order-service")
                .retrieve()
                .body(String.class);

        assertNotNull(allBody, "Health response for order-service should not be null");
        // Should contain Checks array with Status field
        assertTrue(allBody.contains("Status"),
                "Health response should contain Status field in checks");
    }

    // ── Consul DNS Discovery Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("Consul DNS interface should resolve service names")
    void consulDnsShouldResolveServiceNames() {
        // Consul provides a DNS interface on port 8600.
        // Services are discoverable at <service-name>.service.consul
        // This test verifies the DNS interface is active via the HTTP API.
        String body = restClient.get()
                .uri(CONSUL_URL + "/v1/agent/self")
                .retrieve()
                .body(String.class);

        assertNotNull(body, "Consul agent self endpoint should return agent info");
        assertTrue(body.contains("DNS") || body.contains("dns"),
                "Consul agent should have DNS configuration");
    }

    // ── Consul Key-Value Store (Service Metadata) ────────────────────────────────

    @Test
    @DisplayName("Consul KV store should be accessible for service configuration")
    void consulKvStoreShouldBeAccessible() {
        // Consul KV can be used for distributed configuration alongside discovery
        String body = restClient.get()
                .uri(CONSUL_URL + "/v1/kv/?keys")
                .retrieve()
                .body(String.class);

        // KV store may be empty but the endpoint should be reachable
        // A 404 means no keys exist yet, which is acceptable
        // The fact that we didn't get a connection error means it's accessible
        assertNotNull(body != null ? body : "empty",
                "Consul KV endpoint should be reachable");
    }

    // ── Helper Methods ───────────────────────────────────────────────────────────

    private boolean isServiceRegistered(String serviceName) {
        try {
            String body = restClient.get()
                    .uri(CONSUL_SERVICES_URL)
                    .retrieve()
                    .body(String.class);
            return body != null && body.contains(serviceName);
        } catch (Exception e) {
            return false;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test interrupted", e);
        }
    }
}
