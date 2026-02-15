package com.poc.order.adapter.out.rest;

import com.poc.order.application.port.out.PaymentPort;
import com.poc.order.domain.model.Money;
import com.poc.order.domain.model.OrderId;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Outbound REST adapter that implements {@link PaymentPort}.
 * Uses Spring Boot 4's {@link RestClient} to call the Payment Service.
 *
 * <p>Decorated with Resilience4j {@link CircuitBreaker} and {@link Retry}
 * annotations to handle transient failures when communicating with the
 * Payment Service. When the circuit is open or all retries are exhausted,
 * the fallback method returns a {@code FAILED} payment result.</p>
 */
@Component
public class PaymentRestClient implements PaymentPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentRestClient.class);

    private final RestClient restClient;

    public PaymentRestClient(@Value("${payment-service.url:http://localhost:8083}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    @CircuitBreaker(name = "paymentService", fallbackMethod = "processPaymentFallback")
    @Retry(name = "paymentService")
    public PaymentResult processPayment(OrderId orderId, Money amount) {
        PaymentRestMapper.PaymentRestRequest request = PaymentRestMapper.toRequest(orderId, amount);

        PaymentRestMapper.PaymentRestResponse response = restClient.post()
                .uri("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PaymentRestMapper.PaymentRestResponse.class);

        return PaymentRestMapper.fromResponse(response);
    }

    /**
     * Fallback method invoked when the circuit breaker is open or all retries
     * are exhausted. Returns a payment result with {@code FAILED} status.
     */
    private PaymentResult processPaymentFallback(OrderId orderId, Money amount, Throwable ex) {
        log.warn("Payment fallback triggered for order '{}': {}", orderId.id(), ex.getMessage());
        return new PaymentResult("fallback-" + orderId.id(), "FAILED");
    }
}
