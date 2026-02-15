package com.poc.product.application.port.in;

import com.poc.product.application.InventoryCheckResult;
import com.poc.product.domain.model.ProductId;

public interface InventoryCheckUseCase {
    InventoryCheckResult checkInventory(ProductId productId, int quantity);
}
