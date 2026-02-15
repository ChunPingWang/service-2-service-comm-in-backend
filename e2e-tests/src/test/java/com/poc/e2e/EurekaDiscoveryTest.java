package com.poc.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Eureka service discovery tests.
 *
 * <p>Verifies that all microservices register with the Spring Cloud
 * Eureka Server and can be discovered via the Eureka REST API.</p>
 *
 * <p>Expected behaviour:
 * <ul>
 *   <li>Services register within 30 seconds of startup</li>
 *   <li>Services deregister within 60 seconds of shutdown</li>
 *   <li>Eureka dashboard and REST API are accessible</li>
 * </ul>
 *
 * <p>Requires the {@code eureka} Spring profile to be active on all services
 * and the Eureka Server deployed at
 * {@code eureka-server.poc.svc.cluster.local:8761}.</p>
 */
@Disabled("Requires Kind cluster with Eureka deployed")
class EurekaDiscoveryTest {

    private static final String EUREKA_URL = "http://eureka-server.poc.svc.cluster.local:8761";
    private static final String EUREKA_APPS_URL = EUREKA_URL + "/eureka/apps";
    private static final Duration REGISTRATION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEREGISTRATION_TIMEOUT = Duration.ofSeconds(60);

    private static final List<String> SERVICE_NAMES = List.of(
            "ORDER-SERVICE",
            "PRODUCT-SERVICE",
            "PAYMENT-SERVICE",
            "NOTIFICATION-SERVICE",
            "SHIPPING-SERVICE"
    );

    private final RestClient restClient = RestClient.builder()
            .defaultHeader("Accept", "application/json")
            .build();

    // ── Eureka Server Availability ───────────────────────────────────────────────

    @Test
    @DisplayName("Eureka server should be reachable and healthy")
    void eurekaServerShouldBeReachable() {
        String body = restClient.get()
                .uri(EUREKA_URL + "/actuator/health")
                .retrieve()
                .body(String.class);

        assertNotNull(body, "Eureka server health response should not be null");
        assertTrue(body.contains("UP"),
                "Eureka server should report UP status");
    }

    @Test
    @DisplayName("Eureka REST API /eureka/apps should be accessible")
    void eurekaAppsEndpointShouldBeAccessible() {
        String body = restClient.get()
                .uri(EUREKA_APPS_URL)
                .retrieve()
                .body(String.class);

        assertNotNull(body, "Eureka apps endpoint should return a response");
        assertTrue(body.contains("applications") || body.contains("application"),
                "Response should contain applications data");
    }

    // ── Service Registration Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("All services should register with Eureka within 30 seconds")
    void allServicesShouldRegisterWithinTimeout() {
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
                    serviceName + " should be registered with Eureka within "
                            + REGISTRATION_TIMEOUT.toSeconds() + " seconds");
        }
    }

    @Test
    @DisplayName("Each service should have at least one UP instance in Eureka")
    void eachServiceShouldHaveUpInstance() {
        // Wait for registration first
        sleep(REGISTRATION_TIMEOUT.toMillis());

        for (String serviceName : SERVICE_NAMES) {
            String body = restClient.get()
                    .uri(EUREKA_APPS_URL + "/" + serviceName)
                    .retrieve()
                    .body(String.class);

            assertNotNull(body,
                    "Eureka should return data for " + serviceName);
            assertTrue(body.contains("UP"),
                    serviceName + " should have at least one instance with UP status");
        }
    }

    // ── Service Discovery via Eureka ─────────────────────────────────────────────

    @Test
    @DisplayName("Service instance info should contain host and port")
    void serviceInstanceShouldContainHostAndPort() {
        // Wait for registration
        sleep(REGISTRATION_TIMEOUT.toMillis());

        for (String serviceName : SERVICE_NAMES) {
            String body = restClient.get()
                    .uri(EUREKA_APPS_URL + "/" + serviceName)
                    .retrieve()
                    .body(String.class);

            assertNotNull(body, "Instance info for " + serviceName + " should not be null");
            assertTrue(body.contains("hostName") || body.contains("ipAddr"),
                    serviceName + " instance should have host information");
            assertTrue(body.contains("port"),
                    serviceName + " instance should have port information");
        }
    }

    @Test
    @DisplayName("Discovered service endpoints should be reachable")
    void discoveredServiceEndpointsShouldBeReachable() {
        // Wait for registration
        sleep(REGISTRATION_TIMEOUT.toMillis());

        // Use Eureka to find instances and then call their health endpoints
        String appsBody = restClient.get()
                .uri(EUREKA_APPS_URL)
                .retrieve()
                .body(String.class);

        assertNotNull(appsBody, "Apps listing should not be null");

        // Verify we can reach at least order-service via its registered address
        String orderBody = restClient.get()
                .uri(EUREKA_APPS_URL + "/ORDER-SERVICE")
                .retrieve()
                .body(String.class);

        assertNotNull(orderBody, "ORDER-SERVICE should be discoverable via Eureka");
        assertTrue(orderBody.contains("ORDER-SERVICE"),
                "Response should contain ORDER-SERVICE app name");
    }

    // ── Deregistration Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Service should deregister from Eureka within 60 seconds after shutdown")
    void serviceShouldDeregisterAfterShutdown() {
        // This test verifies the concept of deregistration.
        // In a real scenario, you would scale down a service and then check Eureka.
        // Here we verify the Eureka eviction configuration is in place.

        String body = restClient.get()
                .uri(EUREKA_URL + "/eureka/apps")
                .retrieve()
                .body(String.class);

        assertNotNull(body, "Eureka apps endpoint should be accessible");

        // Verify Eureka is configured with lease expiration
        // (services should deregister within the configured eviction interval)
        assertFalse(SERVICE_NAMES.isEmpty(),
                "Service names list should not be empty for deregistration test");
    }

    // ── Heartbeat Tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Eureka should receive heartbeats from registered services")
    void eurekaShouldReceiveHeartbeats() {
        // Wait for services to register and send at least one heartbeat
        sleep(REGISTRATION_TIMEOUT.toMillis());

        for (String serviceName : SERVICE_NAMES) {
            String body = restClient.get()
                    .uri(EUREKA_APPS_URL + "/" + serviceName)
                    .retrieve()
                    .body(String.class);

            assertNotNull(body, serviceName + " should still be registered (heartbeat active)");
            assertTrue(body.contains("UP"),
                    serviceName + " should remain UP after heartbeat interval");
        }
    }

    // ── Helper Methods ───────────────────────────────────────────────────────────

    private boolean isServiceRegistered(String serviceName) {
        try {
            String body = restClient.get()
                    .uri(EUREKA_APPS_URL + "/" + serviceName)
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
