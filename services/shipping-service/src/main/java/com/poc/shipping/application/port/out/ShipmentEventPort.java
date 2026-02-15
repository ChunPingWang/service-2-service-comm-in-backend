package com.poc.shipping.application.port.out;

import com.poc.shipping.domain.event.ShipmentArrangedEvent;

/**
 * Outbound port for publishing shipment domain events.
 *
 * <p>Implemented by infrastructure adapters (e.g., Kafka producer).</p>
 */
public interface ShipmentEventPort {

    /**
     * Publishes a shipment-arranged event to the messaging infrastructure.
     *
     * @param event the shipment-arranged event to publish
     */
    void publish(ShipmentArrangedEvent event);
}
