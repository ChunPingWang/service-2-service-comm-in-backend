package com.poc.order.domain.model;

/**
 * Value object representing a line item within an Order.
 * Validates non-null productId/unitPrice and quantity >= 1.
 */
public record OrderItem(ProductId productId, int quantity, Money unitPrice) {

    public OrderItem {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        if (unitPrice == null) {
            throw new IllegalArgumentException("unitPrice must not be null");
        }
    }

    /**
     * Calculates the line total: unitPrice * quantity.
     */
    public Money lineTotal() {
        return unitPrice.multiply(quantity);
    }
}
