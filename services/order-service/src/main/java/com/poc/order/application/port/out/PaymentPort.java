package com.poc.order.application.port.out;

import com.poc.order.domain.model.Money;
import com.poc.order.domain.model.OrderId;

/**
 * Outbound port for payment processing.
 * Driven adapters (e.g., REST client to Payment Service) implement this interface.
 */
public interface PaymentPort {

    PaymentResult processPayment(OrderId orderId, Money amount);

    /**
     * Result of a payment processing attempt.
     */
    record PaymentResult(String paymentId, String status) {}
}
