package com.poc.order.domain.event;

import java.time.Instant;

/**
 * Domain event representing that an order has been created.
 * Contains only primitives/strings -- no entity references.
 */
public record OrderCreatedEvent(
        String orderId,
        String customerId,
        String productId,
        int quantity,
        String totalAmount,   // serialized decimal e.g. "99.99"
        String currency,
        Instant timestamp
) {

    public OrderCreatedEvent {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        if (totalAmount == null || totalAmount.isBlank()) {
            throw new IllegalArgumentException("totalAmount must not be blank");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
    }
}
