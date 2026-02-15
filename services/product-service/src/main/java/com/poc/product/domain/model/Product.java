package com.poc.product.domain.model;

import java.math.BigDecimal;

/**
 * Aggregate root representing a product in the catalog.
 * <p>
 * Invariants:
 * <ul>
 *   <li>productId, name, price, and category must not be null</li>
 *   <li>name must not be blank</li>
 *   <li>price amount must be strictly positive (greater than zero)</li>
 *   <li>stockQuantity must be non-negative</li>
 *   <li>description defaults to empty string if null</li>
 * </ul>
 */
public record Product(
        ProductId productId,
        String name,
        String description,
        Money price,
        int stockQuantity,
        Category category
) {

    public Product {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (price == null) {
            throw new IllegalArgumentException("price must not be null");
        }
        if (price.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be strictly positive");
        }
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("stockQuantity must not be negative");
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        if (description == null) {
            description = "";
        }
    }

    /**
     * Checks whether the product has enough stock available for the requested quantity.
     *
     * @param quantity the number of units to check (must be at least 1)
     * @return true if stock is sufficient, false otherwise
     * @throws IllegalArgumentException if quantity is less than 1
     */
    public boolean hasAvailableStock(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1, got: " + quantity);
        }
        return stockQuantity >= quantity;
    }

    /**
     * Creates a new Product with stock reduced by the given quantity.
     *
     * @param quantity the number of units to reserve (must be at least 1)
     * @return a new Product instance with reduced stock
     * @throws IllegalStateException if there is insufficient stock
     */
    public Product reserveStock(int quantity) {
        if (!hasAvailableStock(quantity)) {
            throw new IllegalStateException(
                    "Insufficient stock: requested %d but only %d available".formatted(quantity, stockQuantity));
        }
        return new Product(productId, name, description, price, stockQuantity - quantity, category);
    }
}
