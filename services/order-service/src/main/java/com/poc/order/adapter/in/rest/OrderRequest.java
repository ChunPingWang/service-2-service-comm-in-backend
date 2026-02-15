package com.poc.order.adapter.in.rest;

/**
 * Inbound REST request DTO for creating an order.
 * Self-validating: rejects null/blank identifiers and non-positive quantity.
 */
public record OrderRequest(String productId, int quantity, String customerId) {

    public OrderRequest {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
    }
}
