package com.poc.order.application;

/**
 * Self-validating payload object for shipment-arranged events.
 *
 * <p>Co-located in the application package (rather than in {@code port.in})
 * because the ArchUnit architecture rules enforce that all classes in
 * {@code port.in} must be interfaces.</p>
 *
 * @param shipmentId     the shipment identifier (must not be blank)
 * @param orderId        the order identifier (must not be blank)
 * @param trackingNumber the tracking number (must not be blank)
 */
public record ShipmentArrangedPayload(
        String shipmentId,
        String orderId,
        String trackingNumber
) {
    public ShipmentArrangedPayload {
        if (shipmentId == null || shipmentId.isBlank()) {
            throw new IllegalArgumentException("shipmentId required");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId required");
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("trackingNumber required");
        }
    }
}
