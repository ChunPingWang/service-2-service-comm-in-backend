package com.poc.order.application.port.in;

import com.poc.order.domain.model.Order;

/**
 * Inbound port for creating orders.
 * Driving adapters (REST controllers, gRPC handlers) depend on this interface
 * rather than directly on the application service.
 */
public interface CreateOrderUseCase {

    Order createOrder(CreateOrderCommand command);

    /**
     * Self-validating command object for order creation.
     */
    record CreateOrderCommand(String orderId, String customerId, String productId, int quantity) {
        public CreateOrderCommand {
            if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId required");
            if (customerId == null || customerId.isBlank()) throw new IllegalArgumentException("customerId required");
            if (productId == null || productId.isBlank()) throw new IllegalArgumentException("productId required");
            if (quantity < 1) throw new IllegalArgumentException("quantity must be >= 1");
        }
    }
}
