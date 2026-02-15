package com.poc.shipping.application.port.in;

import com.poc.shipping.domain.model.Shipment;
import com.poc.shipping.domain.model.ShipmentId;

import java.util.Optional;

/**
 * Inbound port for querying existing shipments.
 */
public interface QueryShipmentUseCase {

    /**
     * Finds a shipment by its unique identifier.
     *
     * @param shipmentId the shipment identifier to search for
     * @return the shipment if found, or empty
     */
    Optional<Shipment> findById(ShipmentId shipmentId);
}
