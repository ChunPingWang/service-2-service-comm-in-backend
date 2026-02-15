package com.poc.shipping.adapter.out.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.poc.shipping.domain.event.ShipmentArrangedEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps {@link ShipmentArrangedEvent} domain events to Kafka envelope JSON strings.
 *
 * <p>Envelope format:
 * <pre>{
 *   "eventId": "UUID",
 *   "eventType": "SHIPMENT_ARRANGED",
 *   "timestamp": "ISO-8601",
 *   "source": "shipping-service",
 *   "correlationId": "UUID",
 *   "payload": { ... }
 * }</pre>
 */
@Component
public class ShipmentEventMapper {

    private final ObjectMapper objectMapper;

    public ShipmentEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converts a {@link ShipmentArrangedEvent} to a Kafka envelope JSON string.
     *
     * @param event the domain event
     * @return JSON string in envelope format
     * @throws IllegalStateException if JSON serialization fails
     */
    public String toJson(ShipmentArrangedEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "SHIPMENT_ARRANGED");
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("source", "shipping-service");
        envelope.put("correlationId", UUID.randomUUID().toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shipmentId", event.shipmentId());
        payload.put("orderId", event.orderId());
        payload.put("trackingNumber", event.trackingNumber());
        payload.put("status", event.status());

        envelope.put("payload", payload);

        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize ShipmentArrangedEvent to JSON", e);
        }
    }
}
