package com.poc.payment.application.port.in;

import com.poc.payment.application.OrderCreatedPayload;

/**
 * Inbound port for handling order-created events from the messaging infrastructure.
 *
 * <p>When an order is created, the payment service receives the event and
 * automatically processes payment for the order.</p>
 */
public interface HandleOrderCreatedUseCase {

    /**
     * Handles an order-created event by processing payment for the order.
     *
     * @param payload the order-created event payload
     */
    void handleOrderCreated(OrderCreatedPayload payload);
}
