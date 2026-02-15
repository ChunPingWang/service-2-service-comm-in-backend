package com.poc.notification.adapter.out.messaging;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Maps domain data to RabbitMQ message payloads for shipping notifications.
 *
 * <p>Produces JSON messages in the format:
 * <pre>{@code
 * {
 *   "orderId": "ord-001",
 *   "action": "ARRANGE_SHIPMENT"
 * }
 * }</pre>
 */
@Component
public class RabbitMQMessageMapper {

    private final ObjectMapper objectMapper;

    public RabbitMQMessageMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converts an order ID into a shipping notification JSON message string.
     *
     * @param orderId the order identifier to include in the message
     * @return a JSON string with orderId and action fields
     * @throws IllegalStateException if JSON serialization fails
     */
    public String toShippingNotificationJson(String orderId) {
        try {
            Map<String, String> message = Map.of(
                    "orderId", orderId,
                    "action", "ARRANGE_SHIPMENT"
            );
            return objectMapper.writeValueAsString(message);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize shipping notification message", e);
        }
    }
}
