package com.poc.order.application.port.out;

import com.poc.order.domain.event.OrderCreatedEvent;

/**
 * Outbound port for publishing order domain events.
 *
 * <p>Implemented by infrastructure adapters (e.g., Kafka producer, RabbitMQ publisher).</p>
 */
public interface OrderEventPort {

    /**
     * Publishes an order-created event to the messaging infrastructure.
     *
     * @param event the order-created event to publish
     */
    void publish(OrderCreatedEvent event);
}
