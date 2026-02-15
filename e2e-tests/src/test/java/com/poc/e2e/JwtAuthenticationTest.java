package com.poc.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT authentication integration tests.
 *
 * <p>Validates that APISIX gateway enforces JWT authentication via the
 * {@code jwt-auth} plugin on protected routes (orders, payments, shipments).
 * The products route is public and should not require authentication.
 *
 * <p>JWT tokens are generated using HMAC-SHA256 with the test consumer
 * secret configured in the APISIX route configuration.
 *
 * <p>These tests require a running Kind cluster with APISIX deployed
 * and JWT authentication configured on protected routes.
 */
@SpringBootTest(classes = JwtAuthenticationTest.class)
@Disabled("Requires Kind cluster with APISIX deployed")
class JwtAuthenticationTest {

    private static final String GATEWAY_BASE_URL = "http://localhost:30080";

    /**
     * HMAC-SHA256 secret for the test consumer. Must match the secret
     * configured in the APISIX consumer definition (apisix-routes.yaml).
     */
    private static final String JWT_SECRET = "s2s-poc-jwt-secret-key-for-testing";
    private static final String JWT_KEY = "test-consumer-key";
    private static final String ALGORITHM = "HmacSHA256";

    private static RestClient restClient;

    @BeforeAll
    static void setUp() {
        restClient = RestClient.builder()
                .baseUrl(GATEWAY_BASE_URL)
                .build();
    }

    // ---- Protected routes without token should get 401 ----

    @Test
    @DisplayName("Request to /api/v1/orders without JWT token should return 401")
    void ordersWithoutTokenShouldReturn401() {
        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/orders")
                .exchange((req, res) -> res.getStatusCode());

        assertEquals(HttpStatus.UNAUTHORIZED, statusCode,
                "Orders route without JWT should return 401 Unauthorized");
    }

    @Test
    @DisplayName("Request to /api/v1/payments without JWT token should return 401")
    void paymentsWithoutTokenShouldReturn401() {
        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/payments")
                .exchange((req, res) -> res.getStatusCode());

        assertEquals(HttpStatus.UNAUTHORIZED, statusCode,
                "Payments route without JWT should return 401 Unauthorized");
    }

    @Test
    @DisplayName("Request to /api/v1/shipments without JWT token should return 401")
    void shipmentsWithoutTokenShouldReturn401() {
        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/shipments")
                .exchange((req, res) -> res.getStatusCode());

        assertEquals(HttpStatus.UNAUTHORIZED, statusCode,
                "Shipments route without JWT should return 401 Unauthorized");
    }

    // ---- Public route should succeed without token ----

    @Test
    @DisplayName("Request to /api/v1/products without JWT token should succeed (public route)")
    void productsWithoutTokenShouldSucceed() {
        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/products")
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(statusCode.is2xxSuccessful() || statusCode.isSameCodeAs(HttpStatus.NOT_FOUND),
                "Products route (public) should be accessible without JWT (expected 2xx or 404, got " + statusCode + ")");
    }

    // ---- Protected routes with valid token should succeed ----

    @Test
    @DisplayName("Request to /api/v1/orders with valid JWT token should succeed")
    void ordersWithValidTokenShouldSucceed() {
        String token = generateJwtToken(JWT_KEY, JWT_SECRET, Instant.now().plusSeconds(3600));

        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + token)
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(
                statusCode.is2xxSuccessful() || statusCode.isSameCodeAs(HttpStatus.NOT_FOUND),
                "Orders route with valid JWT should succeed (expected 2xx or 404, got " + statusCode + ")"
        );
    }

    @Test
    @DisplayName("Request to /api/v1/payments with valid JWT token should succeed")
    void paymentsWithValidTokenShouldSucceed() {
        String token = generateJwtToken(JWT_KEY, JWT_SECRET, Instant.now().plusSeconds(3600));

        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .exchange((req, res) -> res.getStatusCode());

        assertTrue(
                statusCode.is2xxSuccessful() || statusCode.isSameCodeAs(HttpStatus.NOT_FOUND),
                "Payments route with valid JWT should succeed (expected 2xx or 404, got " + statusCode + ")"
        );
    }

    // ---- Expired token should get 401 ----

    @Test
    @DisplayName("Request with expired JWT token should return 401")
    void expiredTokenShouldReturn401() {
        // Generate a token that expired 1 hour ago
        String expiredToken = generateJwtToken(JWT_KEY, JWT_SECRET, Instant.now().minusSeconds(3600));

        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + expiredToken)
                .exchange((req, res) -> res.getStatusCode());

        assertEquals(HttpStatus.UNAUTHORIZED, statusCode,
                "Expired JWT token should return 401 Unauthorized");
    }

    // ---- Invalid token should get 401 ----

    @Test
    @DisplayName("Request with invalid JWT token should return 401")
    void invalidTokenShouldReturn401() {
        // Generate a token with a wrong secret
        String invalidToken = generateJwtToken(JWT_KEY, "wrong-secret-key-definitely-invalid", Instant.now().plusSeconds(3600));

        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + invalidToken)
                .exchange((req, res) -> res.getStatusCode());

        assertEquals(HttpStatus.UNAUTHORIZED, statusCode,
                "Invalid JWT token (wrong secret) should return 401 Unauthorized");
    }

    @Test
    @DisplayName("Request with malformed JWT token should return 401")
    void malformedTokenShouldReturn401() {
        HttpStatusCode statusCode = restClient.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer not.a.valid.jwt.token")
                .exchange((req, res) -> res.getStatusCode());

        assertEquals(HttpStatus.UNAUTHORIZED, statusCode,
                "Malformed JWT token should return 401 Unauthorized");
    }

    // ---- JWT token generation helper (HMAC-SHA256) ----

    /**
     * Generates a minimal JWT token using HMAC-SHA256.
     *
     * <p>Token structure follows the standard JWT format:
     * {@code base64url(header).base64url(payload).base64url(signature)}
     *
     * @param key    the consumer key (included in payload as "key" claim)
     * @param secret the HMAC-SHA256 signing secret
     * @param exp    the expiration time
     * @return a signed JWT token string
     */
    private static String generateJwtToken(String key, String secret, Instant exp) {
        try {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

            // Header
            String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            String encodedHeader = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8));

            // Payload
            String payload = "{\"key\":\"" + key + "\","
                    + "\"exp\":" + exp.getEpochSecond() + ","
                    + "\"iat\":" + Instant.now().getEpochSecond() + "}";
            String encodedPayload = encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));

            // Signature
            String signingInput = encodedHeader + "." + encodedPayload;
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] signatureBytes = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            String encodedSignature = encoder.encodeToString(signatureBytes);

            return signingInput + "." + encodedSignature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }
}
