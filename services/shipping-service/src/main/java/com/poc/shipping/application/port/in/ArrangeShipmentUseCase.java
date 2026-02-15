package com.poc.shipping.application.port.in;

import com.poc.shipping.domain.model.Shipment;

/**
 * Inbound port for arranging shipments when payment is completed.
 */
public interface ArrangeShipmentUseCase {

    /**
     * Arranges a new shipment for the given order.
     *
     * @param orderId the order identifier to ship
     * @return the created and shipped Shipment
     */
    Shipment arrangeShipment(String orderId);
}
