package com.poc.shipping.domain.model;

/**
 * Context-local value object representing an order identifier
 * within the Shipping bounded context.
 */
public record OrderId(String id) {

    public OrderId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
