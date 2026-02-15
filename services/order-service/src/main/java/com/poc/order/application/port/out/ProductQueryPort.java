package com.poc.order.application.port.out;

import com.poc.order.domain.model.Money;
import com.poc.order.domain.model.ProductId;

/**
 * Outbound port for querying product information.
 * Driven adapters (e.g., gRPC client to Product Service) implement this interface.
 */
public interface ProductQueryPort {

    ProductInfo getProduct(ProductId productId);

    /**
     * Read-only projection of product data needed by the Order bounded context.
     */
    record ProductInfo(ProductId productId, String name, Money price, int stockQuantity) {}
}
