package com.poc.order.domain.model;

/**
 * Value object representing an Order identifier.
 * Validates non-blank.
 */
public record OrderId(String id) {

    public OrderId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("OrderId must not be blank");
        }
    }
}
