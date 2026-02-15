package com.poc.order.domain.model;

/**
 * Value object representing a Customer identifier.
 * Validates non-blank.
 */
public record CustomerId(String id) {

    public CustomerId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("CustomerId must not be blank");
        }
    }
}
