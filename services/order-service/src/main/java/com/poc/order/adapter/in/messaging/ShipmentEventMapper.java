package com.poc.order.adapter.in.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.poc.order.application.ShipmentArrangedPayload;
import org.springframework.stereotype.Component;

/**
 * Parses a shipment-arranged Kafka envelope JSON and extracts
 * a {@link ShipmentArrangedPayload} from the payload section.
 */
@Component
public class ShipmentEventMapper {

    private final ObjectMapper objectMapper;

    public ShipmentEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses the JSON envelope and extracts the shipment-arranged payload.
     *
     * @param json the raw JSON message from Kafka
     * @return the extracted payload
     * @throws IllegalStateException if JSON parsing fails
     */
    public ShipmentArrangedPayload fromJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode payload = root.get("payload");

            String shipmentId = payload.get("shipmentId").asText();
            String orderId = payload.get("orderId").asText();
            String trackingNumber = payload.get("trackingNumber").asText();

            return new ShipmentArrangedPayload(shipmentId, orderId, trackingNumber);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to parse shipment.arranged event", e);
        }
    }
}
