package com.poc.shipping.adapter.in.messaging;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Maps RabbitMQ message JSON strings to domain-relevant data.
 *
 * <p>Expects shipping notification messages in the format:
 * <pre>{@code
 * {
 *   "orderId": "ord-001",
 *   "action": "ARRANGE_SHIPMENT"
 * }
 * }</pre>
 */
@Component
public class ShippingMessageMapper {

    private final ObjectMapper objectMapper;

    public ShippingMessageMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Extracts the order ID from a shipping notification JSON message.
     *
     * @param json the raw RabbitMQ message body (JSON string)
     * @return the extracted order identifier
     * @throws IllegalArgumentException if the JSON cannot be parsed or orderId is missing
     */
    public String extractOrderId(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String orderId = root.path("orderId").asText();
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalArgumentException("orderId is missing from shipping notification message");
            }
            return orderId;
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to parse shipping notification message", e);
        }
    }
}
