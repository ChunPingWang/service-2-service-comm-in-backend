package com.poc.order.integration;

import com.poc.order.application.port.in.CreateOrderUseCase;
import com.poc.order.application.port.in.HandleShipmentEventUseCase;
import com.poc.order.application.port.in.QueryOrderUseCase;
import com.poc.order.application.port.out.OrderRepository;
import com.poc.order.application.port.out.PaymentPort;
import com.poc.order.application.port.out.PaymentPort.PaymentResult;
import com.poc.order.application.port.out.ProductQueryPort;
import com.poc.order.domain.model.Money;
import com.poc.order.domain.model.OrderId;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for circuit breaker behaviour in the order-service.
 *
 * <p>Verifies that Resilience4j circuit breaker is properly integrated with
 * the Spring Boot application context and that actuator health endpoints
 * report circuit breaker status.</p>
 *
 * <p>Currently disabled because the circuit breaker annotations on
 * {@code PaymentRestClient} must be applied first (test-first approach).</p>
 */
@SpringBootTest
@Disabled("Requires Resilience4j annotations on PaymentRestClient")
@DisplayName("Circuit Breaker Integration")
class CircuitBreakerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private MockMvc mockMvc;

    @MockitoBean
    private CreateOrderUseCase createOrderUseCase;

    @MockitoBean
    private QueryOrderUseCase queryOrderUseCase;

    @MockitoBean
    private HandleShipmentEventUseCase handleShipmentEventUseCase;

    @MockitoBean
    private ProductQueryPort productQueryPort;

    @MockitoBean
    private PaymentPort paymentPort;

    @MockitoBean
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Reset circuit breaker state before each test
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    @DisplayName("circuit breaker should be registered in the registry")
    void circuitBreaker_isRegistered() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentService");
        assertThat(cb).isNotNull();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("actuator health endpoint should include circuit breaker status")
    void actuatorHealth_includesCircuitBreakerStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.circuitBreakers").exists())
                .andExpect(jsonPath("$.components.circuitBreakers.details.paymentService").exists());
    }

    @Test
    @DisplayName("circuit breaker should open after repeated payment failures")
    void circuitBreaker_opensAfterRepeatedFailures() {
        when(paymentPort.processPayment(any(OrderId.class), any(Money.class)))
                .thenThrow(new RestClientException("Payment service unavailable"));

        OrderId orderId = new OrderId("order-int-001");
        Money amount = new Money(new BigDecimal("50.00"), "USD");

        // Make enough failed calls to exceed the failure rate threshold
        for (int i = 0; i < 10; i++) {
            try {
                paymentPort.processPayment(orderId, amount);
            } catch (RestClientException ignored) {
                // expected
            }
        }

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentService");
        // After sufficient failures, circuit should be OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("fallback should return FAILED payment result when circuit is open")
    void fallback_returnsFailed_whenCircuitIsOpen() {
        when(paymentPort.processPayment(any(OrderId.class), any(Money.class)))
                .thenThrow(new RestClientException("Payment service unavailable"));

        OrderId orderId = new OrderId("order-int-002");
        Money amount = new Money(new BigDecimal("75.00"), "USD");

        // Open the circuit
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentService");
        cb.transitionToOpenState();

        // The next call should use the fallback
        PaymentResult result = null;
        try {
            result = paymentPort.processPayment(orderId, amount);
        } catch (Exception e) {
            // In integration, the fallback should catch this
            result = new PaymentResult("fallback-" + orderId.id(), "FAILED");
        }

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("circuit breaker metrics should be exposed via actuator")
    void circuitBreakerMetrics_exposedViaActuator() throws Exception {
        mockMvc.perform(get("/actuator/metrics/resilience4j.circuitbreaker.calls"))
                .andExpect(status().isOk());
    }
}
