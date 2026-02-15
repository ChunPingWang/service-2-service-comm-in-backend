package com.poc.product.adapter.in.rest;

import com.poc.product.application.InventoryCheckResult;
import com.poc.product.domain.model.Product;

/**
 * Mapper that converts domain objects to REST response records.
 */
final class ProductRestMapper {

    private ProductRestMapper() {
        // utility class
    }

    /**
     * Converts a domain {@link Product} to a {@link ProductRestResponse}.
     */
    static ProductRestResponse toProductRestResponse(Product product) {
        return new ProductRestResponse(
                product.productId().id(),
                product.name(),
                product.description(),
                new MoneyDto(
                        product.price().amount().doubleValue(),
                        product.price().currency()
                ),
                product.stockQuantity(),
                product.category().name()
        );
    }

    /**
     * Converts an {@link InventoryCheckResult} to an {@link InventoryRestResponse}.
     */
    static InventoryRestResponse toInventoryRestResponse(InventoryCheckResult result) {
        return new InventoryRestResponse(result.available(), result.remainingStock());
    }

    /**
     * REST response DTO representing a product.
     */
    record ProductRestResponse(
            String id,
            String name,
            String description,
            MoneyDto price,
            int stock,
            String category
    ) {}

    /**
     * REST response DTO representing a monetary amount.
     */
    record MoneyDto(
            double amount,
            String currency
    ) {}

    /**
     * REST response DTO representing an inventory check result.
     */
    record InventoryRestResponse(
            boolean available,
            int remainingStock
    ) {}
}
