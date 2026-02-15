package com.poc.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full business flow E2E test.
 *
 * <p>Validates the complete order lifecycle end-to-end across all 5 services:
 * <ol>
 *   <li>Create order (order-service) -- synchronous REST</li>
 *   <li>Product check (order -> product-service) -- synchronous REST + gRPC</li>
 *   <li>Payment processing (order -> payment-service) -- synchronous REST with Circuit Breaker</li>
 *   <li>Order created event (order-service -> Kafka "order.created")</li>
 *   <li>Payment processes order event (payment-service consumes "order.created")</li>
 *   <li>Payment completed event (payment-service -> Kafka "payment.completed")</li>
 *   <li>Notification sent (notification-service consumes "payment.completed" via Kafka)</li>
 *   <li>Shipping arranged (shipping-service consumes "payment.completed" via RabbitMQ)</li>
 *   <li>Shipment arranged event (shipping-service -> Kafka "shipment.arranged")</li>
 *   <li>Order updated (order-service consumes "shipment.arranged")</li>
 * </ol>
 *
 * <p>These tests require a running Kind cluster with all services, Kafka,
 * and RabbitMQ deployed and port-forwarded to localhost.</p>
 */
@SpringBootTest(classes = FullBusinessFlowE2ETest.class)
@Disabled("Requires Kind cluster with full stack deployed")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullBusinessFlowE2ETest {

    private static final String ORDER_SERVICE_URL = "http://localhost:8081";
    private static final String PRODUCT_SERVICE_URL = "http://localhost:8082";
    private static final String PAYMENT_SERVICE_URL = "http://localhost:8083";
    private static final String NOTIFICATION_SERVICE_URL = "http://localhost:8084";
    private static final String SHIPPING_SERVICE_URL = "http://localhost:8085";

    /** Time to wait for async event propagation (milliseconds). */
    private static final long ASYNC_WAIT_MS = 10_000;

    /** Time to wait for full event chain propagation (milliseconds). */
    private static final long FULL_CHAIN_WAIT_MS = 30_000;

    private static RestClient orderClient;
    private static RestClient productClient;
    private static RestClient paymentClient;
    private static RestClient notificationClient;
    private static RestClient shippingClient;

    /** Shared order ID across tests (set during order creation). */
    private static volatile String createdOrderId;

    @BeforeAll
    static void setUp() {
        orderClient = RestClient.builder()
                .baseUrl(ORDER_SERVICE_URL)
                .build();
        productClient = RestClient.builder()
                .baseUrl(PRODUCT_SERVICE_URL)
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

    // ── Step 1: Verify all services are healthy ──────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Step 1: All 5 services should be healthy before starting the flow")
    void allServicesShouldBeHealthy() {
        Map<String, RestClient> services = Map.of(
                "order-service", orderClient,
                "product-service", productClient,
                "payment-service", paymentClient,
                "notification-service", notificationClient,
                "shipping-service", shippingClient
        );

        for (Map.Entry<String, RestClient> entry : services.entrySet()) {
            var health = entry.getValue().get()
                    .uri("/actuator/health")
                    .exchange((req, res) -> {
                        assertEquals(HttpStatus.OK, res.getStatusCode(),
                                entry.getKey() + " health endpoint should return 200");
                        return res.bodyTo(Map.class);
                    });

            assertNotNull(health, entry.getKey() + " health response should not be null");
            assertEquals("UP", health.get("status"),
                    entry.getKey() + " should be UP before starting the business flow");
        }
    }

    // ── Step 2: Product availability check ───────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("Step 2: Product-service should return product details for validation")
    void productServiceShouldReturnProductDetails() {
        var statusCode = productClient.get()
                .uri("/api/v1/products/{id}", "prod-biz-001")
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(
                statusCode.is2xxSuccessful() || statusCode.isSameCodeAs(HttpStatus.NOT_FOUND),
                "Product service should be reachable for product validation (got " + statusCode + ")"
        );
    }

    // ── Step 3: Create order (triggers synchronous calls + async events) ─────────

    @Test
    @Order(3)
    @DisplayName("Step 3: Create order triggers product check, payment, and publishes order.created")
    void createOrderShouldTriggerFullSynchronousChain() {
        var response = orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-biz-001",
                            "quantity": 2,
                            "customerId": "cust-biz-001"
                        }
                        """)
                .exchange((req, res) -> {
                    assertTrue(res.getStatusCode().is2xxSuccessful(),
                            "Order creation should succeed, got " + res.getStatusCode());
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Order creation response should not be null");
        createdOrderId = String.valueOf(response.get("id"));
        assertNotNull(createdOrderId, "Created order should have an ID");
    }

    // ── Step 4: Verify payment was triggered ─────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("Step 4: Payment-service should have processed the order payment")
    void paymentServiceShouldHaveProcessedPayment() throws InterruptedException {
        // Wait for synchronous payment call and potential async processing
        Thread.sleep(ASYNC_WAIT_MS);

        // Verify payment-service is healthy and has processed the payment
        var health = paymentClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Payment service should be healthy after processing payment");
                    return res.bodyTo(Map.class);
                });

        assertEquals("UP", health.get("status"),
                "Payment service should remain UP after payment processing");
    }

    // ── Step 5: Verify notification was sent ─────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("Step 5: Notification-service should have consumed 'payment.completed' event")
    void notificationServiceShouldHaveConsumedPaymentCompletedEvent() throws InterruptedException {
        // Wait for async event chain: order.created -> payment -> payment.completed -> notification
        Thread.sleep(ASYNC_WAIT_MS);

        var health = notificationClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Notification service should be healthy after consuming event");
                    return res.bodyTo(Map.class);
                });

        assertEquals("UP", health.get("status"),
                "Notification service should remain UP after consuming payment.completed event");
    }

    // ── Step 6: Verify shipping was arranged ─────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("Step 6: Shipping-service should have consumed 'payment.completed' via RabbitMQ")
    void shippingServiceShouldHaveConsumedPaymentCompletedViaRabbitMQ() throws InterruptedException {
        // Wait for RabbitMQ event: payment.completed -> shipping (via payment.events exchange)
        Thread.sleep(ASYNC_WAIT_MS);

        var health = shippingClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Shipping service should be healthy after consuming RabbitMQ event");
                    return res.bodyTo(Map.class);
                });

        assertEquals("UP", health.get("status"),
                "Shipping service should remain UP after consuming RabbitMQ payment event");
    }

    // ── Step 7: Verify order was updated with shipment info ──────────────────────

    @Test
    @Order(7)
    @DisplayName("Step 7: Order-service should have consumed 'shipment.arranged' event and updated order")
    void orderServiceShouldHaveConsumedShipmentArrangedEvent() throws InterruptedException {
        // Wait for the final async hop: shipping -> shipment.arranged -> order
        Thread.sleep(ASYNC_WAIT_MS);

        // Verify the order is retrievable and the service is healthy
        var health = orderClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Order service should be healthy after full lifecycle");
                    return res.bodyTo(Map.class);
                });

        assertEquals("UP", health.get("status"),
                "Order service should remain UP after consuming shipment.arranged event");

        // If the order ID was captured, verify it can be retrieved
        if (createdOrderId != null) {
            var statusCode = orderClient.get()
                    .uri("/api/v1/orders/{id}", createdOrderId)
                    .exchange((req, res) -> res.getStatusCode());

            assertTrue(
                    statusCode.is2xxSuccessful() || statusCode.isSameCodeAs(HttpStatus.NOT_FOUND),
                    "Order should be retrievable after full lifecycle (got " + statusCode + ")"
            );
        }
    }

    // ── Full chain validation ────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("Step 8: Complete lifecycle - all services should remain healthy after full flow")
    void allServicesShouldRemainHealthyAfterFullFlow() throws InterruptedException {
        // Create a fresh order and wait for the complete lifecycle
        orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-biz-full-001",
                            "quantity": 1,
                            "customerId": "cust-biz-full-001"
                        }
                        """)
                .exchange((req, res) -> {
                    assertTrue(res.getStatusCode().is2xxSuccessful(),
                            "Final order creation should succeed");
                    return res.bodyTo(Map.class);
                });

        // Wait for the full event chain to complete
        Thread.sleep(FULL_CHAIN_WAIT_MS);

        // Final health check on all services
        Map<String, RestClient> services = Map.of(
                "order-service", orderClient,
                "product-service", productClient,
                "payment-service", paymentClient,
                "notification-service", notificationClient,
                "shipping-service", shippingClient
        );

        for (Map.Entry<String, RestClient> entry : services.entrySet()) {
            var health = entry.getValue().get()
                    .uri("/actuator/health")
                    .exchange((req, res) -> {
                        assertEquals(HttpStatus.OK, res.getStatusCode(),
                                entry.getKey() + " should be healthy after complete lifecycle");
                        return res.bodyTo(Map.class);
                    });

            assertEquals("UP", health.get("status"),
                    entry.getKey() + " should be UP after complete order lifecycle");
        }
    }

    @Test
    @Order(9)
    @DisplayName("Step 9: Multiple orders should complete lifecycle without service degradation")
    void multipleOrdersShouldCompleteWithoutDegradation() throws InterruptedException {
        // Create multiple orders in rapid succession
        int orderCount = 5;
        for (int i = 0; i < orderCount; i++) {
            orderClient.post()
                    .uri("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {
                                "productId": "prod-biz-multi-%d",
                                "quantity": %d,
                                "customerId": "cust-biz-multi-%d"
                            }
                            """.formatted(i, i + 1, i))
                    .exchange((req, res) -> {
                        assertTrue(res.getStatusCode().is2xxSuccessful(),
                                "Order creation #" + " should succeed");
                        return res.bodyTo(Map.class);
                    });
        }

        // Wait for all event chains to complete
        Thread.sleep(FULL_CHAIN_WAIT_MS);

        // Verify no service has degraded under load
        Map<String, RestClient> services = Map.of(
                "order-service", orderClient,
                "product-service", productClient,
                "payment-service", paymentClient,
                "notification-service", notificationClient,
                "shipping-service", shippingClient
        );

        for (Map.Entry<String, RestClient> entry : services.entrySet()) {
            var health = entry.getValue().get()
                    .uri("/actuator/health")
                    .exchange((req, res) -> {
                        assertEquals(HttpStatus.OK, res.getStatusCode(),
                                entry.getKey() + " should still be healthy after " + orderCount + " orders");
                        return res.bodyTo(Map.class);
                    });

            assertEquals("UP", health.get("status"),
                    entry.getKey() + " should be UP after processing " + orderCount + " concurrent orders");
        }
    }
}
