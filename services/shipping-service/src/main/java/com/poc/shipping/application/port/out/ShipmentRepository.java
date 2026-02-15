package com.poc.shipping.application.port.out;

import com.poc.shipping.domain.model.Shipment;
import com.poc.shipping.domain.model.ShipmentId;

import java.util.Optional;

/**
 * Outbound port for shipment persistence.
 *
 * <p>Implemented by infrastructure adapters (e.g., in-memory store, database).</p>
 */
public interface ShipmentRepository {

    /**
     * Persists the given shipment, returning the saved instance.
     *
     * @param shipment the shipment to save
     * @return the saved shipment
     */
    Shipment save(Shipment shipment);

    /**
     * Finds a shipment by its unique identifier.
     *
     * @param shipmentId the shipment identifier to search for
     * @return the shipment if found, or empty
     */
    Optional<Shipment> findById(ShipmentId shipmentId);
}
