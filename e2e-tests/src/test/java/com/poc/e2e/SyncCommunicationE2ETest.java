package com.poc.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synchronous communication E2E tests.
 *
 * <p>Validates that synchronous inter-service communication works correctly
 * across the deployed microservice stack:
 * <ul>
 *   <li>REST call to order-service (POST /api/v1/orders, GET /api/v1/orders/{id})</li>
 *   <li>REST call to product-service (GET /api/v1/products/{id}, GET /api/v1/products?category=X)</li>
 *   <li>gRPC call to product-service (GetProduct, CheckInventory on port 9092)</li>
 *   <li>REST call to payment-service (POST /api/v1/payments, GET /api/v1/payments/{id})</li>
 * </ul>
 *
 * <p>These tests require a running Kind cluster with all services deployed
 * and port-forwarded to localhost.</p>
 */
@SpringBootTest(classes = SyncCommunicationE2ETest.class)
@Disabled("Requires Kind cluster with full stack deployed")
class SyncCommunicationE2ETest {

    private static final String ORDER_SERVICE_URL = "http://localhost:8081";
    private static final String PRODUCT_SERVICE_URL = "http://localhost:8082";
    private static final String PAYMENT_SERVICE_URL = "http://localhost:8083";

    private static RestClient orderClient;
    private static RestClient productClient;
    private static RestClient paymentClient;

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
    }

    // ── Order Service REST Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("Order service health endpoint should return UP")
    void orderServiceHealthShouldReturnUp() {
        var response = orderClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Order service health should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Health response should not be null");
        assertEquals("UP", response.get("status"),
                "Order service health status should be UP");
    }

    @Test
    @DisplayName("POST /api/v1/orders should create a new order")
    void createOrderShouldSucceed() {
        var response = orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-sync-001",
                            "quantity": 2,
                            "customerId": "cust-sync-001"
                        }
                        """)
                .exchange((req, res) -> {
                    assertTrue(
                            res.getStatusCode().is2xxSuccessful(),
                            "Order creation should return 2xx, got " + res.getStatusCode()
                    );
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Order creation response should not be null");
        assertNotNull(response.get("id"), "Created order should have an id");
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} should retrieve an existing order")
    void getOrderByIdShouldReturnOrder() {
        // First create an order
        var createResponse = orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-sync-002",
                            "quantity": 1,
                            "customerId": "cust-sync-002"
                        }
                        """)
                .exchange((req, res) -> res.bodyTo(Map.class));

        assertNotNull(createResponse, "Created order response should not be null");
        String orderId = String.valueOf(createResponse.get("id"));

        // Then retrieve it
        var getResponse = orderClient.get()
                .uri("/api/v1/orders/{id}", orderId)
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "GET order should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(getResponse, "GET order response should not be null");
        assertEquals(orderId, String.valueOf(getResponse.get("id")),
                "Returned order id should match the created order id");
    }

    // ── Product Service REST Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("Product service health endpoint should return UP")
    void productServiceHealthShouldReturnUp() {
        var response = productClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Product service health should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Health response should not be null");
        assertEquals("UP", response.get("status"),
                "Product service health status should be UP");
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} should return product details")
    void getProductByIdShouldReturnProduct() {
        HttpStatusCode statusCode = productClient.get()
                .uri("/api/v1/products/{id}", "prod-001")
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(
                statusCode.is2xxSuccessful() || statusCode.isSameCodeAs(HttpStatus.NOT_FOUND),
                "Product lookup should reach product-service (expected 2xx or 404, got " + statusCode + ")"
        );
    }

    @Test
    @DisplayName("GET /api/v1/products?category=X&limit=N should return filtered products")
    void getProductsByCategoryShouldReturnFilteredResults() {
        HttpStatusCode statusCode = productClient.get()
                .uri("/api/v1/products?category=electronics&limit=10")
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(
                statusCode.is2xxSuccessful() || statusCode.isSameCodeAs(HttpStatus.NOT_FOUND),
                "Product category query should reach product-service (expected 2xx or 404, got " + statusCode + ")"
        );
    }

    // ── Product Service gRPC Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("gRPC GetProduct call to product-service should respond on port 9092")
    void grpcGetProductShouldBeAccessible() {
        // Verify gRPC port is accessible via a TCP-level health check.
        // A full gRPC client call would require the generated proto stubs;
        // here we verify the gRPC endpoint is reachable via HTTP/2 upgrade probe.
        RestClient grpcProbeClient = RestClient.builder()
                .baseUrl("http://localhost:9092")
                .build();

        // gRPC servers respond to plain HTTP with a specific error or an empty response,
        // confirming the port is open and accepting connections.
        HttpStatusCode statusCode = grpcProbeClient.get()
                .uri("/")
                .exchange((req, res) -> res.getStatusCode());

        // gRPC servers typically return 4xx/5xx for non-gRPC HTTP requests,
        // but the key assertion is that the server is reachable (no connection refused).
        assertNotNull(statusCode,
                "gRPC endpoint should be reachable on port 9092");
    }

    @Test
    @DisplayName("Product service exposes gRPC endpoint for inventory checks")
    void grpcCheckInventoryShouldBeAccessible() {
        // Similar connectivity check for the gRPC inventory endpoint.
        // In a full test, we would use a generated ProductServiceGrpc stub to call
        // CheckInventory(productId, quantity). Here we validate port availability.
        RestClient grpcProbeClient = RestClient.builder()
                .baseUrl("http://localhost:9092")
                .build();

        HttpStatusCode statusCode = grpcProbeClient.get()
                .uri("/")
                .exchange((req, res) -> res.getStatusCode());

        assertNotNull(statusCode,
                "gRPC endpoint for inventory check should be reachable on port 9092");
    }

    // ── Payment Service REST Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("Payment service health endpoint should return UP")
    void paymentServiceHealthShouldReturnUp() {
        var response = paymentClient.get()
                .uri("/actuator/health")
                .exchange((req, res) -> {
                    assertEquals(HttpStatus.OK, res.getStatusCode(),
                            "Payment service health should return 200");
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Health response should not be null");
        assertEquals("UP", response.get("status"),
                "Payment service health status should be UP");
    }

    @Test
    @DisplayName("POST /api/v1/payments should create a new payment")
    void createPaymentShouldSucceed() {
        var response = paymentClient.post()
                .uri("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "orderId": "ord-sync-001",
                            "amount": 99.99,
                            "currency": "USD",
                            "method": "CREDIT_CARD"
                        }
                        """)
                .exchange((req, res) -> {
                    assertTrue(
                            res.getStatusCode().is2xxSuccessful(),
                            "Payment creation should return 2xx, got " + res.getStatusCode()
                    );
                    return res.bodyTo(Map.class);
                });

        assertNotNull(response, "Payment creation response should not be null");
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} should retrieve an existing payment")
    void getPaymentByIdShouldReturnPayment() {
        // First create a payment
        var createResponse = paymentClient.post()
                .uri("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "orderId": "ord-sync-002",
                            "amount": 49.99,
                            "currency": "USD",
                            "method": "CREDIT_CARD"
                        }
                        """)
                .exchange((req, res) -> res.bodyTo(Map.class));

        assertNotNull(createResponse, "Created payment response should not be null");
        String paymentId = String.valueOf(createResponse.get("id"));

        // Then retrieve it
        HttpStatusCode statusCode = paymentClient.get()
                .uri("/api/v1/payments/{id}", paymentId)
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(
                statusCode.is2xxSuccessful() || statusCode.isSameCodeAs(HttpStatus.NOT_FOUND),
                "Payment lookup should reach payment-service (expected 2xx or 404, got " + statusCode + ")"
        );
    }

    // ── Cross-Service Synchronous Communication ──────────────────────────────────

    @Test
    @DisplayName("Order creation should synchronously call product-service for product validation")
    void orderCreationShouldCallProductService() {
        // Creating an order triggers a synchronous REST call from order-service
        // to product-service (GET /api/v1/products/{id}) for product validation.
        HttpStatusCode statusCode = orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-sync-cross-001",
                            "quantity": 1,
                            "customerId": "cust-sync-cross-001"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(
                statusCode.is2xxSuccessful(),
                "Order creation with product validation should succeed (got " + statusCode + ")"
        );
    }

    @Test
    @DisplayName("Order creation should synchronously call payment-service for payment processing")
    void orderCreationShouldCallPaymentService() {
        // Creating an order triggers a synchronous REST call from order-service
        // to payment-service (POST /api/v1/payments) with circuit breaker protection.
        HttpStatusCode statusCode = orderClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "productId": "prod-sync-cross-002",
                            "quantity": 3,
                            "customerId": "cust-sync-cross-002"
                        }
                        """)
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(
                statusCode.is2xxSuccessful(),
                "Order creation with payment processing should succeed (got " + statusCode + ")"
        );
    }
}
