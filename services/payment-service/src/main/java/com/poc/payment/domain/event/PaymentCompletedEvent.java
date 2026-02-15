package com.poc.payment.domain.event;

import java.time.Instant;

/**
 * Domain event representing that a payment has been completed.
 * Contains only primitives/strings -- no entity references.
 */
public record PaymentCompletedEvent(
        String paymentId,
        String orderId,
        String amount,      // serialized decimal e.g. "99.99"
        String currency,
        Instant timestamp
) {

    public PaymentCompletedEvent {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("paymentId must not be blank");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("amount must not be blank");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
    }
}
