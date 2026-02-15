package com.poc.product.domain.model;

/**
 * Value object representing a unique product identifier.
 */
public record ProductId(String id) {

    public ProductId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ProductId must not be blank");
        }
    }
}
