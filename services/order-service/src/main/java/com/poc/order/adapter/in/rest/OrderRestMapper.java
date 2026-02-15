package com.poc.order.adapter.in.rest;

import com.poc.order.domain.model.Order;
import com.poc.order.domain.model.OrderItem;

/**
 * Maps between the domain {@link Order} model and REST DTOs.
 * Stateless utility class -- all methods are static.
 */
public final class OrderRestMapper {

    private OrderRestMapper() {
        // prevent instantiation
    }

    /**
     * Converts a domain {@link Order} to an {@link OrderResponse}.
     * For this PoC, orders contain a single item; the first item's
     * productId and quantity are extracted into the flat response structure.
     */
    public static OrderResponse toResponse(Order order) {
        OrderItem firstItem = order.items().getFirst();
        var totalAmount = order.totalAmount();

        return new OrderResponse(
                order.orderId().id(),
                order.customerId().id(),
                firstItem.productId().id(),
                firstItem.quantity(),
                new OrderResponse.MoneyResponse(
                        totalAmount.amount().doubleValue(),
                        totalAmount.currency()
                ),
                order.status().name(),
                order.createdAt().toString(),
                order.updatedAt().toString()
        );
    }
}
