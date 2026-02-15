package com.poc.payment.domain.model;

/**
 * Value object representing an order identifier.
 * This is a context-local copy for the Payment bounded context.
 */
public record OrderId(String id) {

    public OrderId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
    }
}
