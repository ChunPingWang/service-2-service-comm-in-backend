package com.poc.order.domain.model;

/**
 * Value object representing a Product identifier (context-local copy for Order bounded context).
 * Validates non-blank.
 */
public record ProductId(String id) {

    public ProductId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ProductId must not be blank");
        }
    }
}
