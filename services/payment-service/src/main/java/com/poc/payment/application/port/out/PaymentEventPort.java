package com.poc.payment.application.port.out;

import com.poc.payment.domain.event.PaymentCompletedEvent;

/**
 * Outbound port for publishing payment domain events.
 *
 * <p>Implemented by infrastructure adapters (e.g., Kafka producer, RabbitMQ publisher).</p>
 */
public interface PaymentEventPort {

    /**
     * Publishes a payment-completed event to the messaging infrastructure.
     *
     * @param event the payment-completed event to publish
     */
    void publish(PaymentCompletedEvent event);
}
