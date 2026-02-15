package com.poc.payment.integration;

import com.poc.payment.application.ProcessPaymentCommand;
import com.poc.payment.application.port.in.HandleOrderCreatedUseCase;
import com.poc.payment.application.port.in.ProcessPaymentUseCase;
import com.poc.payment.application.port.in.QueryPaymentUseCase;
import com.poc.payment.application.port.out.PaymentEventPort;
import com.poc.payment.application.port.out.PaymentRepository;
import com.poc.payment.domain.model.Money;
import com.poc.payment.domain.model.OrderId;
import com.poc.payment.domain.model.Payment;
import com.poc.payment.domain.model.PaymentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test for the Payment REST adapter.
 *
 * <p>Boots the full Spring context with a random port and uses mocked
 * inbound ports to verify HTTP request/response mapping.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentRestIntegrationTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private ProcessPaymentUseCase processPaymentUseCase;

    @MockitoBean
    private QueryPaymentUseCase queryPaymentUseCase;

    @MockitoBean
    private HandleOrderCreatedUseCase handleOrderCreatedUseCase;

    @MockitoBean
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PaymentEventPort paymentEventPort;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/payments returns 200 with valid payment response")
    @SuppressWarnings("unchecked")
    void postPayment_returns200() {
        // Arrange: create a completed payment that the use case will return
        Payment completedPayment = Payment.create(
                new PaymentId("pay-001"),
                new OrderId("ord-001"),
                new Money(new BigDecimal("100.00"), "USD")
        ).complete();

        when(processPaymentUseCase.processPayment(any(ProcessPaymentCommand.class)))
                .thenReturn(completedPayment);

        Map<String, Object> requestBody = Map.of(
                "orderId", "ord-001",
                "amount", Map.of(
                        "amount", 100.00,
                        "currency", "USD"
                )
        );

        // Act
        Map<String, Object> response = restClient.post()
                .uri("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        // Assert
        assertNotNull(response);
        assertEquals("pay-001", response.get("id"));
        assertEquals("ord-001", response.get("orderId"));
        assertEquals("COMPLETED", response.get("status"));
        assertNotNull(response.get("createdAt"));
        assertNotNull(response.get("completedAt"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} returns 200 when payment exists")
    @SuppressWarnings("unchecked")
    void getPayment_returns200WhenFound() {
        // Arrange
        Payment payment = Payment.create(
                new PaymentId("pay-002"),
                new OrderId("ord-002"),
                new Money(new BigDecimal("50.00"), "EUR")
        ).complete();

        when(queryPaymentUseCase.findById(new PaymentId("pay-002")))
                .thenReturn(Optional.of(payment));

        // Act
        Map<String, Object> response = restClient.get()
                .uri("/api/v1/payments/pay-002")
                .retrieve()
                .body(Map.class);

        // Assert
        assertNotNull(response);
        assertEquals("pay-002", response.get("id"));
        assertEquals("ord-002", response.get("orderId"));
        assertEquals("COMPLETED", response.get("status"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} returns 404 when payment not found")
    @SuppressWarnings("unchecked")
    void getPayment_returns404WhenNotFound() {
        // Arrange
        when(queryPaymentUseCase.findById(new PaymentId("pay-nonexistent")))
                .thenReturn(Optional.empty());

        // Act
        Map<String, Object> response = restClient.get()
                .uri("/api/v1/payments/pay-nonexistent")
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {
                    // Do not throw on 404, we handle it in assertions
                })
                .body(Map.class);

        // Assert: response should be null for 404 with empty body
        assertNull(response);
    }
}
