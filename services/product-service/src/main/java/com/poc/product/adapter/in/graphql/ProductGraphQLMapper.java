package com.poc.product.adapter.in.graphql;

import com.poc.product.application.InventoryCheckResult;
import com.poc.product.domain.model.Product;

/**
 * Mapper that converts domain objects to GraphQL DTO records.
 */
final class ProductGraphQLMapper {

    private ProductGraphQLMapper() {
        // utility class
    }

    /**
     * Converts a domain {@link Product} to a {@link ProductDto} for GraphQL responses.
     */
    static ProductDto toProductDto(Product product) {
        return new ProductDto(
                product.productId().id(),
                product.name(),
                product.description(),
                product.price().amount().doubleValue(),
                product.price().currency(),
                product.stockQuantity(),
                product.category().name()
        );
    }

    /**
     * Converts an {@link InventoryCheckResult} to an {@link InventoryCheckDto} for GraphQL responses.
     */
    static InventoryCheckDto toInventoryCheckDto(InventoryCheckResult result) {
        return new InventoryCheckDto(result.available(), result.remainingStock());
    }

    /**
     * GraphQL DTO representing a product.
     */
    record ProductDto(
            String id,
            String name,
            String description,
            double price,
            String currency,
            int stock,
            String category
    ) {}

    /**
     * GraphQL DTO representing an inventory check result.
     */
    record InventoryCheckDto(
            boolean available,
            int remainingStock
    ) {}
}
