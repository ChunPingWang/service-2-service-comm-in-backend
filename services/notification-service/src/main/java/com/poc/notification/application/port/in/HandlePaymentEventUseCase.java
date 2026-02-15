package com.poc.notification.application.port.in;

import com.poc.notification.application.PaymentCompletedPayload;

/**
 * Inbound port for handling payment events from the messaging infrastructure.
 *
 * <p>When a payment is completed, the notification service receives the event
 * and creates a notification for the customer.</p>
 */
public interface HandlePaymentEventUseCase {

    /**
     * Handles a payment-completed event by creating and sending a notification.
     *
     * @param payload the payment-completed event payload
     */
    void handlePaymentCompleted(PaymentCompletedPayload payload);
}
