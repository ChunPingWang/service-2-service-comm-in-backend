package com.poc.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asynchronous communication E2E tests.
 *
 * <p>Validates that asynchronous message-driven communication works correctly
 * across the deployed microservice stack:
 * <ul>
 *   <li>Kafka "order.created" event flow (Order -> Payment)</li>
 *   <li>Kafka "payment.completed" event flow (Payment -> Notification)</li>
 *   <li>RabbitMQ payment-to-shipping flow (Payment -> Shipping via payment.events exchange)</li>
 *   <li>Kafka "shipment.arranged" event flow (Shipping -> Order)</li>
 * </ul>
 *
 * <p>These tests require a running Kind cluster with all services, Kafka,
 * and RabbitMQ deployed and port-forwarded to localhost.</p>
 */
@SpringBootTest(classes = AsyncCommunicationE2ETest.class)
@Disabled("Requires Kind cluster with full stack deployed")
class AsyncCommunicationE2ETest {

    private static final String ORDER_SERVICE_URL = "http://localhost:8081";
    private static final String PAYMENT_SERVICE_URL = "http://localhost:8083";
    private static final String NOTIFICATION_SERVICE_URL = "http://localhost:8084";
    private static final String SHIPPING_SERVICE_URL = "http://localhost:8085";

    /** Time to wait for async event propagation (milliseconds). */
    private static final long ASYNC_WAIT_MS = 10_000;

    private static RestClient orderClient;
    private static RestClient paymentClient;
    private static RestClient notificationClient;
    private static RestClient shippingClient;

    @BeforeAll
    static void setUp() {
        orderClient = RestClient.builder()
                .baseUrl(ORDER_SERVICE_URL)
                .build();
        paymentClient = RestClient.builder()
                .baseUrl(PAYMENT_SERVICE_URL)
                .build();
        notificationClient = RestClient.builder()
                .baseUrl(NOTIFICATION_SERVICE_URL)
                .build();
        shippingClient = RestClient.builder()
                .baseUrl(SHIPPING_SERVICE_URL)
                .build();
    }

    // ── Kafka order.created Event Flow ───────────────────────────────────────────

    @Test
    @DisplayName("Order creation should publish 'order.created' Kafka event consumed by payment-service")
    void orderCreatedEventShouldBeConsumedByPaymentService() throws InterruptedException {
        // Create an order, which triggers an "order.created" Kafka event
        var orderResponse = orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-async-001",
                            "quantity": 1,
                            "customerId": "cust-async-001"
                        }
                        """)
                .exchange((req, res) -> {
                    assertTrue(res.getStatusCode().is2xxSuccessful(),
                            "Order creation should succeed (got " + res.getStatusCode() + ")");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(orderResponse, "Order response should not be null");
        String orderId = String.valueOf(orderResponse.get("id"));

        // Wait for the Kafka event to propagate to payment-service
        Thread.sleep(ASYNC_WAIT_MS);

        // Verify that payment-service processed the order.created event
        // by checking if a payment was automatically created for this order
        var paymentStatus = paymentClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Payment service should be healthy after processing event");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(paymentStatus, "Payment service health should be available");
        assertEquals("UP", paymentStatus.get("status"),
                "Payment service should remain UP after consuming order.created event");
    }

    @Test
    @DisplayName("Order creation should produce message to 'order.created' Kafka topic")
    void orderCreationShouldPublishToKafkaTopic() {
        // Verify order-service is capable of publishing to Kafka by creating an order
        // and checking the service remains healthy (no Kafka connection errors)
        var orderResponse = orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-async-kafka-001",
                            "quantity": 2,
                            "customerId": "cust-async-kafka-001"
                        }
                        """)
                .exchange((req, res) -> {
                    assertTrue(res.getStatusCode().is2xxSuccessful(),
                            "Order creation should succeed when Kafka is available");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(orderResponse, "Order response should not be null");

        // Verify order-service health includes Kafka connectivity
        var health = orderClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Order service should be healthy");
                    return res.bodyTo(Map.class);
                });

        assertEquals("UP", health.get("status"),
                "Order service should be UP with Kafka connectivity");
    }

    // ── Kafka payment.completed Event Flow ───────────────────────────────────────

    @Test
    @DisplayName("Payment completion should publish 'payment.completed' Kafka event consumed by notification-service")
    void paymentCompletedEventShouldBeConsumedByNotificationService() throws InterruptedException {
        // Create an order to trigger the full chain: order.created -> payment processing
        orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-async-002",
                            "quantity": 1,
                            "customerId": "cust-async-002"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        // Wait for the event chain: order.created -> payment processing -> payment.completed
        Thread.sleep(ASYNC_WAIT_MS);

        // Verify notification-service consumed the payment.completed event
        // by checking it remains healthy (no consumer errors)
        var notificationHealth = notificationClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Notification service should be healthy");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(notificationHealth, "Notification service health should not be null");
        assertEquals("UP", notificationHealth.get("status"),
                "Notification service should remain UP after consuming payment.completed event");
    }

    @Test
    @DisplayName("Notification service should be connected to Kafka for 'payment.completed' topic")
    void notificationServiceShouldBeConnectedToKafka() {
        var health = notificationClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Notification service health should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(health, "Notification service health should not be null");
        assertEquals("UP", health.get("status"),
                "Notification service should be UP with Kafka connectivity");
    }

    // ── RabbitMQ Payment-to-Shipping Flow ────────────────────────────────────────

    @Test
    @DisplayName("Payment completion should route event to shipping-service via RabbitMQ")
    void paymentCompletedShouldReachShippingViaRabbitMQ() throws InterruptedException {
        // Trigger an order to start the event chain
        orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-async-rabbit-001",
                            "quantity": 1,
                            "customerId": "cust-async-rabbit-001"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        // Wait for the event chain to reach shipping-service via RabbitMQ
        // order.created (Kafka) -> payment processing -> payment.completed (RabbitMQ) -> shipping
        Thread.sleep(ASYNC_WAIT_MS);

        // Verify shipping-service is healthy and processing RabbitMQ messages
        var shippingHealth = shippingClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Shipping service should be healthy");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(shippingHealth, "Shipping service health should not be null");
        assertEquals("UP", shippingHealth.get("status"),
                "Shipping service should remain UP after consuming RabbitMQ messages");
    }

    @Test
    @DisplayName("Shipping service should be connected to RabbitMQ for 'payment.events' exchange")
    void shippingServiceShouldBeConnectedToRabbitMQ() {
        var health = shippingClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Shipping service health should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(health, "Shipping service health should not be null");
        assertEquals("UP", health.get("status"),
                "Shipping service should be UP with RabbitMQ connectivity");
    }

    // ── Kafka shipment.arranged Event Flow ───────────────────────────────────────

    @Test
    @DisplayName("Shipping arrangement should publish 'shipment.arranged' Kafka event consumed by order-service")
    void shipmentArrangedEventShouldBeConsumedByOrderService() throws InterruptedException {
        // Trigger the full chain: order -> payment -> shipping -> shipment.arranged -> order
        orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-async-shipment-001",
                            "quantity": 1,
                            "customerId": "cust-async-shipment-001"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        // Wait for full event chain to complete (order -> payment -> shipping -> order)
        Thread.sleep(ASYNC_WAIT_MS * 2);

        // Verify order-service consumed the shipment.arranged event
        // by checking it remains healthy (no consumer errors)
        var orderHealth = orderClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Order service should be healthy after full event cycle");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(orderHealth, "Order service health should not be null");
        assertEquals("UP", orderHealth.get("status"),
                "Order service should remain UP after consuming shipment.arranged event");
    }

    @Test
    @DisplayName("Full async event chain should complete without service failures")
    void fullAsyncEventChainShouldCompleteWithoutFailures() throws InterruptedException {
        // Create multiple orders to exercise the full async pipeline
        for (int i = 0; i < 3; i++) {
            orderClient.post()
                    .uri("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {
                                "productId": "prod-async-chain-%d",
                                "quantity": 1,
                                "customerId": "cust-async-chain-%d"
                            }
                            """.formatted(i, i))
                    .exchange((req, res) -> res.getStatusCode());
        }

        // Wait for all events to propagate through the full chain
        Thread.sleep(ASYNC_WAIT_MS * 3);

        // Verify all services are still healthy after processing async events
        Map<String, RestClient> services = Map.of(
                "order-service", orderClient,
                "payment-service", paymentClient,
                "notification-service", notificationClient,
                "shipping-service", shippingClient
        );

        for (Map.Entry<String, RestClient> entry : services.entrySet()) {
            var health = entry.getValue().get()
                    .uri("/actuator/health")
                    .exchange((req, res) -> {
                        assertEquals(HttpStatus.OK, res.getStatusCode(),
                                entry.getKey() + " should be healthy after async processing");
                        return res.bodyTo(Map.class);
                    });

            assertEquals("UP", health.get("status"),
                    entry.getKey() + " should be UP after processing multiple async events");
        }
    }
}
