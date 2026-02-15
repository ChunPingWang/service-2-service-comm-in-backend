package com.poc.order.unit.application;

import com.poc.order.application.port.out.PaymentPort;
import com.poc.order.application.port.out.PaymentPort.PaymentResult;
import com.poc.order.domain.model.Money;
import com.poc.order.domain.model.OrderId;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Resilience4j circuit breaker and retry behaviour
 * applied to Payment Service calls.
 *
 * <p>Uses Resilience4j API directly (no Spring annotations) so that
 * state transitions can be verified deterministically.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Circuit Breaker for PaymentPort")
class CircuitBreakerTest {

    @Mock
    private PaymentPort paymentPort;

    private static final OrderId ORDER_ID = new OrderId("order-cb-001");
    private static final Money AMOUNT = new Money(new BigDecimal("100.00"), "USD");

    @Nested
    @DisplayName("Circuit breaker state transitions")
    class StateTransitions {

        private CircuitBreaker circuitBreaker;

        @BeforeEach
        void setUp() {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(5)
                    .failureRateThreshold(50)
                    .minimumNumberOfCalls(5)
                    .waitDurationInOpenState(Duration.ofSeconds(1))
                    .permittedNumberOfCallsInHalfOpenState(2)
                    .build();

            CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
            circuitBreaker = registry.circuitBreaker("paymentService-unit");
        }

        @Test
        @DisplayName("should start in CLOSED state")
        void initialState_isClosed() {
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("should remain CLOSED when failures are below threshold")
        void remainsClosed_whenFailuresBelowThreshold() {
            // 2 failures + 3 successes = 40% failure rate (below 50% threshold)
            when(paymentPort.processPayment(any(), any()))
                    .thenThrow(new RestClientException("Connection refused"))
                    .thenThrow(new RestClientException("Connection refused"))
                    .thenReturn(new PaymentResult("pay-1", "SUCCESS"))
                    .thenReturn(new PaymentResult("pay-2", "SUCCESS"))
                    .thenReturn(new PaymentResult("pay-3", "SUCCESS"));

            Supplier<PaymentResult> decorated = CircuitBreaker.decorateSupplier(
                    circuitBreaker,
                    () -> paymentPort.processPayment(ORDER_ID, AMOUNT)
            );

            // First 2 calls fail
            for (int i = 0; i < 2; i++) {
                try {
                    decorated.get();
                } catch (RestClientException ignored) {
                    // expected
                }
            }
            // Next 3 calls succeed
            for (int i = 0; i < 3; i++) {
                decorated.get();
            }

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("should transition CLOSED -> OPEN after reaching failure rate threshold")
        void transitionsToOpen_whenFailureRateExceedsThreshold() {
            when(paymentPort.processPayment(any(), any()))
                    .thenThrow(new RestClientException("Connection refused"));

            Supplier<PaymentResult> decorated = CircuitBreaker.decorateSupplier(
                    circuitBreaker,
                    () -> paymentPort.processPayment(ORDER_ID, AMOUNT)
            );

            // 5 consecutive failures => 100% failure rate => circuit opens
            for (int i = 0; i < 5; i++) {
                try {
                    decorated.get();
                } catch (Exception ignored) {
                    // expected
                }
            }

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("should transition OPEN -> HALF_OPEN after wait duration elapses")
        void transitionsToHalfOpen_afterWaitDuration() throws InterruptedException {
            when(paymentPort.processPayment(any(), any()))
                    .thenThrow(new RestClientException("Connection refused"));

            Supplier<PaymentResult> decorated = CircuitBreaker.decorateSupplier(
                    circuitBreaker,
                    () -> paymentPort.processPayment(ORDER_ID, AMOUNT)
            );

            // Open the circuit
            for (int i = 0; i < 5; i++) {
                try {
                    decorated.get();
                } catch (Exception ignored) {
                    // expected
                }
            }
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Wait for the waitDurationInOpenState (1 second) to elapse
            Thread.sleep(1200);

            // Manually transition to HALF_OPEN (or it happens on next call attempt)
            circuitBreaker.transitionToHalfOpenState();
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        }

        @Test
        @DisplayName("should transition HALF_OPEN -> CLOSED when calls succeed")
        void transitionsFromHalfOpenToClosed_whenCallsSucceed() throws InterruptedException {
            when(paymentPort.processPayment(any(), any()))
                    .thenThrow(new RestClientException("Connection refused"))
                    .thenThrow(new RestClientException("Connection refused"))
                    .thenThrow(new RestClientException("Connection refused"))
                    .thenThrow(new RestClientException("Connection refused"))
                    .thenThrow(new RestClientException("Connection refused"))
                    // After HALF_OPEN, calls succeed
                    .thenReturn(new PaymentResult("pay-1", "SUCCESS"))
                    .thenReturn(new PaymentResult("pay-2", "SUCCESS"));

            Supplier<PaymentResult> decorated = CircuitBreaker.decorateSupplier(
                    circuitBreaker,
                    () -> paymentPort.processPayment(ORDER_ID, AMOUNT)
            );

            // Open the circuit with 5 failures
            for (int i = 0; i < 5; i++) {
                try {
                    decorated.get();
                } catch (Exception ignored) {
                    // expected
                }
            }
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Wait and transition to HALF_OPEN
            Thread.sleep(1200);
            circuitBreaker.transitionToHalfOpenState();
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

            // Permitted calls in HALF_OPEN succeed => transitions to CLOSED
            for (int i = 0; i < 2; i++) {
                decorated.get();
            }

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("should transition HALF_OPEN -> OPEN when calls fail again")
        void transitionsFromHalfOpenToOpen_whenCallsFail() throws InterruptedException {
            when(paymentPort.processPayment(any(), any()))
                    .thenThrow(new RestClientException("Connection refused"));

            Supplier<PaymentResult> decorated = CircuitBreaker.decorateSupplier(
                    circuitBreaker,
                    () -> paymentPort.processPayment(ORDER_ID, AMOUNT)
            );

            // Open the circuit
            for (int i = 0; i < 5; i++) {
                try {
                    decorated.get();
                } catch (Exception ignored) {
                    // expected
                }
            }
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Wait and transition to HALF_OPEN
            Thread.sleep(1200);
            circuitBreaker.transitionToHalfOpenState();
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

            // Calls in HALF_OPEN fail => back to OPEN
            for (int i = 0; i < 2; i++) {
                try {
                    decorated.get();
                } catch (Exception ignored) {
                    // expected
                }
            }

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("Fallback behaviour")
    class Fallback {

        private CircuitBreaker circuitBreaker;

        @BeforeEach
        void setUp() {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(5)
                    .failureRateThreshold(50)
                    .minimumNumberOfCalls(5)
                    .waitDurationInOpenState(Duration.ofSeconds(60))
                    .build();

            circuitBreaker = CircuitBreakerRegistry.of(config).circuitBreaker("paymentService-fallback");
        }

        @Test
        @DisplayName("should return fallback result when circuit is OPEN")
        void returnsFallback_whenCircuitIsOpen() {
            when(paymentPort.processPayment(any(), any()))
                    .thenThrow(new RestClientException("Service unavailable"));

            Supplier<PaymentResult> decorated = CircuitBreaker.decorateSupplier(
                    circuitBreaker,
                    () -> paymentPort.processPayment(ORDER_ID, AMOUNT)
            );

            // Open the circuit
            for (int i = 0; i < 5; i++) {
                try {
                    decorated.get();
                } catch (Exception ignored) {
                    // expected
                }
            }
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Now calls should be rejected => use fallback
            PaymentResult fallbackResult = null;
            try {
                decorated.get();
            } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
                // Simulate the fallback that PaymentRestClient will provide
                fallbackResult = new PaymentResult("fallback-" + ORDER_ID.id(), "FAILED");
            }

            assertThat(fallbackResult).isNotNull();
            assertThat(fallbackResult.status()).isEqualTo("FAILED");
            assertThat(fallbackResult.paymentId()).startsWith("fallback-");
        }
    }

    @Nested
    @DisplayName("Retry mechanism")
    class RetryMechanism {

        @Test
        @DisplayName("should retry on RestClientException and succeed after transient failure")
        void retriesAndSucceeds() {
            when(paymentPort.processPayment(any(), any()))
                    .thenThrow(new RestClientException("Connection reset"))
                    .thenThrow(new RestClientException("Connection reset"))
                    .thenReturn(new PaymentResult("pay-retry-ok", "SUCCESS"));

            RetryConfig retryConfig = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(100))
                    .retryExceptions(RestClientException.class)
                    .build();

            Retry retry = RetryRegistry.of(retryConfig).retry("paymentService-retry");

            Supplier<PaymentResult> decorated = Retry.decorateSupplier(
                    retry,
                    () -> paymentPort.processPayment(ORDER_ID, AMOUNT)
            );

            PaymentResult result = decorated.get();

            assertThat(result.paymentId()).isEqualTo("pay-retry-ok");
            assertThat(result.status()).isEqualTo("SUCCESS");
            verify(paymentPort, times(3)).processPayment(ORDER_ID, AMOUNT);
        }

        @Test
        @DisplayName("should exhaust retries and throw when all attempts fail")
        void exhaustsRetriesAndThrows() {
            when(paymentPort.processPayment(any(), any()))
                    .thenThrow(new RestClientException("Service down"));

            RetryConfig retryConfig = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(100))
                    .retryExceptions(RestClientException.class)
                    .build();

            Retry retry = RetryRegistry.of(retryConfig).retry("paymentService-exhaust");

            Supplier<PaymentResult> decorated = Retry.decorateSupplier(
                    retry,
                    () -> paymentPort.processPayment(ORDER_ID, AMOUNT)
            );

            assertThatThrownBy(decorated::get)
                    .isInstanceOf(RestClientException.class)
                    .hasMessageContaining("Service down");

            verify(paymentPort, times(3)).processPayment(ORDER_ID, AMOUNT);
        }

        @Test
        @DisplayName("should not retry on non-retryable exceptions")
        void doesNotRetry_onNonRetryableException() {
            when(paymentPort.processPayment(any(), any()))
                    .thenThrow(new IllegalArgumentException("Invalid order"));

            RetryConfig retryConfig = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(100))
                    .retryExceptions(RestClientException.class)
                    .build();

            Retry retry = RetryRegistry.of(retryConfig).retry("paymentService-no-retry");

            Supplier<PaymentResult> decorated = Retry.decorateSupplier(
                    retry,
                    () -> paymentPort.processPayment(ORDER_ID, AMOUNT)
            );

            assertThatThrownBy(decorated::get)
                    .isInstanceOf(IllegalArgumentException.class);

            // Should only be called once (no retries for non-retryable exceptions)
            verify(paymentPort, times(1)).processPayment(ORDER_ID, AMOUNT);
        }
    }
}
