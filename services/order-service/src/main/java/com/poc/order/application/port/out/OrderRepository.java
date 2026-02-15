package com.poc.order.application.port.out;

import com.poc.order.domain.model.Order;
import com.poc.order.domain.model.OrderId;

import java.util.Optional;

/**
 * Outbound port for order persistence.
 * Driven adapters (e.g., JPA, in-memory) implement this interface.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(OrderId orderId);
}
