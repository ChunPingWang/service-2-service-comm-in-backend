package com.poc.shipping.domain.model;

/**
 * Value object representing a unique shipment identifier.
 */
public record ShipmentId(String id) {

    public ShipmentId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
