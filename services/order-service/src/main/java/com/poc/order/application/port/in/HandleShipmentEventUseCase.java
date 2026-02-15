package com.poc.order.application.port.in;

import com.poc.order.application.ShipmentArrangedPayload;

/**
 * Inbound port for handling shipment-arranged events from the messaging infrastructure.
 *
 * <p>When a shipment is arranged, the order service receives the event and
 * transitions the order to SHIPPED status.</p>
 */
public interface HandleShipmentEventUseCase {

    /**
     * Handles a shipment-arranged event by marking the order as shipped.
     *
     * @param payload the shipment-arranged event payload
     */
    void handleShipmentArranged(ShipmentArrangedPayload payload);
}
