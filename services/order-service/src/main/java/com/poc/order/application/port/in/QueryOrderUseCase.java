package com.poc.order.application.port.in;

import com.poc.order.domain.model.Order;
import com.poc.order.domain.model.OrderId;

import java.util.Optional;

/**
 * Inbound port for querying orders.
 * Driving adapters use this interface to retrieve existing orders.
 */
public interface QueryOrderUseCase {

    Optional<Order> findById(OrderId orderId);
}
