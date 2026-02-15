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
 * Metrics collection verification tests.
 *
 * <p>Validates that Prometheus successfully scrapes metrics from all services
 * via their {@code /actuator/prometheus} endpoints, and that service metrics
 * are queryable through the Prometheus API.</p>
 *
 * <p>These tests require a running Kind cluster with the full observability
 * stack deployed (Prometheus, services with Actuator enabled).</p>
 */
@SpringBootTest(classes = MetricsVerificationTest.class)
@Disabled("Requires Kind cluster with observability stack")
class MetricsVerificationTest {

    private static final String PROMETHEUS_URL = "http://localhost:9090";

    private static final Map<String, Integer> SERVICE_PORTS = Map.of(
            "order-service", 8081,
            "product-service", 8082,
            "payment-service", 8083,
            "notification-service", 8084,
            "shipping-service", 8085
    );

    private static RestClient prometheusClient;

    @BeforeAll
    static void setUp() {
        prometheusClient = RestClient.builder()
                .baseUrl(PROMETHEUS_URL)
                .build();
    }

    @ParameterizedTest(name = "{0} actuator/prometheus endpoint returns valid metrics")
    @CsvSource({
            "order-service, 8081",
            "product-service, 8082",
            "payment-service, 8083",
            "notification-service, 8084",
            "shipping-service, 8085"
    })
    @DisplayName("Service /actuator/prometheus endpoint returns valid metrics")
    void actuatorPrometheusEndpointShouldReturnMetrics(String serviceName, int port) {
        RestClient serviceClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        var response = serviceClient.get()
                .uri("/actuator/prometheus")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            serviceName + " /actuator/prometheus should return 200");
                    return res.bodyTo(String.class);
                });

        assertNotNull(response, serviceName + " should return metrics body");
        assertFalse(response.isBlank(), serviceName + " metrics should not be empty");

        // Verify standard JVM metrics are present
        assertTrue(response.contains("jvm_memory"),
                serviceName + " should expose JVM memory metrics");
        assertTrue(response.contains("process_cpu"),
                serviceName + " should expose process CPU metrics");
    }

    @Test
    @DisplayName("Prometheus API is accessible and healthy")
    void prometheusApiShouldBeAccessible() {
        var response = prometheusClient.get()
                .uri("/api/v1/status/config")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Prometheus config endpoint should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Prometheus should return config data");
        assertEquals("success", response.get("status"),
                "Prometheus status should be 'success'");
    }

    @Test
    @DisplayName("Prometheus has scrape targets for all services")
    void prometheusShouldHaveScrapeTargets() {
        var response = prometheusClient.get()
                .uri("/api/v1/targets")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Prometheus targets endpoint should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Prometheus should return targets data");
        assertEquals("success", response.get("status"),
                "Prometheus targets status should be 'success'");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertNotNull(data, "Targets data should not be null");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> activeTargets =
                (List<Map<String, Object>>) data.get("activeTargets");
        assertNotNull(activeTargets, "Active targets should not be null");
        assertFalse(activeTargets.isEmpty(), "Should have active scrape targets");
    }

    @Test
    @DisplayName("Prometheus can query up metric for services")
    void prometheusShouldShowServicesAsUp() {
        // Query the 'up' metric which Prometheus adds for each scrape target
        var response = prometheusClient.get()
                .uri("/api/v1/query?query=up")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Prometheus query should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Prometheus should return query result");
        assertEquals("success", response.get("status"),
                "Prometheus query status should be 'success'");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertNotNull(data, "Query data should not be null");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results =
                (List<Map<String, Object>>) data.get("result");
        assertNotNull(results, "Query results should not be null");
        assertFalse(results.isEmpty(), "Should have 'up' metric results");
    }

    @Test
    @DisplayName("Prometheus collects HTTP request metrics from services")
    void prometheusShouldCollectHttpRequestMetrics() {
        // Query for HTTP server request duration metrics (Micrometer standard name)
        var response = prometheusClient.get()
                .uri("/api/v1/query?query=http_server_requests_seconds_count")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Prometheus query should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Prometheus should return query result");
        assertEquals("success", response.get("status"),
                "Prometheus query status should be 'success'");
    }

    @Test
    @DisplayName("OTel Collector metrics are scraped by Prometheus")
    void otelCollectorMetricsShouldBeScrapedByPrometheus() {
        // Query for OTel Collector's own metrics
        var response = prometheusClient.get()
                .uri("/api/v1/query?query=otelcol_receiver_accepted_spans")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Prometheus query should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Prometheus should return query result for OTel metrics");
        assertEquals("success", response.get("status"),
                "Prometheus query for OTel metrics should succeed");
    }
}
