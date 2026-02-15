package com.poc.shipping.domain.model;

import java.time.Instant;

/**
 * Aggregate root representing a shipment.
 * Immutable record -- state transitions return new instances.
 */
public record Shipment(
        ShipmentId shipmentId,
        OrderId orderId,
        ShipmentStatus status,
        String trackingNumber,
        Instant createdAt
) {

    public Shipment {
        if (shipmentId == null) {
            throw new IllegalArgumentException("shipmentId must not be null");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }

        // IN_TRANSIT and DELIVERED require a non-blank trackingNumber
        if ((status == ShipmentStatus.IN_TRANSIT || status == ShipmentStatus.DELIVERED)
                && (trackingNumber == null || trackingNumber.isBlank())) {
            throw new IllegalArgumentException(
                    status + " shipment must have a non-blank trackingNumber");
        }
    }

    /**
     * Factory method to create a new Shipment in {@link ShipmentStatus#PENDING} status.
     *
     * @param shipmentId unique shipment identifier
     * @param orderId    the order this shipment is for
     * @return a new Shipment in PENDING status with null trackingNumber
     */
    public static Shipment create(ShipmentId shipmentId, OrderId orderId) {
        if (shipmentId == null) {
            throw new IllegalArgumentException("shipmentId must not be null");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        return new Shipment(shipmentId, orderId, ShipmentStatus.PENDING, null, Instant.now());
    }

    /**
     * Transitions this shipment from PENDING to IN_TRANSIT.
     *
     * @param trackingNumber the tracking number for the shipment (must not be blank)
     * @return a new Shipment with IN_TRANSIT status
     * @throws IllegalStateException    if the shipment is not in PENDING status
     * @throws IllegalArgumentException if trackingNumber is null or blank
     */
    public Shipment ship(String trackingNumber) {
        if (status != ShipmentStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot ship: shipment is in %s status, expected PENDING".formatted(status));
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("trackingNumber must not be blank");
        }
        return new Shipment(shipmentId, orderId, ShipmentStatus.IN_TRANSIT, trackingNumber, createdAt);
    }

    /**
     * Transitions this shipment from IN_TRANSIT to DELIVERED.
     *
     * @return a new Shipment with DELIVERED status
     * @throws IllegalStateException if the shipment is not in IN_TRANSIT status
     */
    public Shipment deliver() {
        if (status != ShipmentStatus.IN_TRANSIT) {
            throw new IllegalStateException(
                    "Cannot deliver: shipment is in %s status, expected IN_TRANSIT".formatted(status));
        }
        return new Shipment(shipmentId, orderId, ShipmentStatus.DELIVERED, trackingNumber, createdAt);
    }
}
