package com.poc.shipping.domain.event;

import java.time.Instant;

/**
 * Domain event representing that a shipment has been arranged and is in transit.
 * Contains only primitives/strings -- no entity references.
 */
public record ShipmentArrangedEvent(
        String shipmentId,
        String orderId,
        String trackingNumber,
        String status,
        Instant timestamp
) {

    public ShipmentArrangedEvent {
        if (shipmentId == null || shipmentId.isBlank()) {
            throw new IllegalArgumentException("shipmentId must not be blank");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("trackingNumber must not be blank");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
    }
}
