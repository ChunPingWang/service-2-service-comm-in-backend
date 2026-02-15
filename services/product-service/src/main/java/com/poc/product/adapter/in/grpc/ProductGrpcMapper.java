package com.poc.product.adapter.in.grpc;

import com.poc.product.application.InventoryCheckResult;
import com.poc.product.domain.model.Product;
import com.poc.product.grpc.InventoryResponse;
import com.poc.product.grpc.ProductResponse;

/**
 * Mapper that converts domain objects to gRPC response messages.
 */
final class ProductGrpcMapper {

    private ProductGrpcMapper() {
        // utility class
    }

    /**
     * Converts a domain {@link Product} to a gRPC {@link ProductResponse}.
     */
    static ProductResponse toProductResponse(Product product) {
        return ProductResponse.newBuilder()
                .setProductId(product.productId().id())
                .setName(product.name())
                .setDescription(product.description())
                .setPrice(product.price().amount().doubleValue())
                .setCurrency(product.price().currency())
                .setStock(product.stockQuantity())
                .setCategory(product.category().name())
                .build();
    }

    /**
     * Converts an {@link InventoryCheckResult} to a gRPC {@link InventoryResponse}.
     */
    static InventoryResponse toInventoryResponse(InventoryCheckResult result) {
        return InventoryResponse.newBuilder()
                .setAvailable(result.available())
                .setRemainingStock(result.remainingStock())
                .build();
    }
}
