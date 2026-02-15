package com.poc.order.adapter.out.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.poc.order.domain.event.OrderCreatedEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps {@link OrderCreatedEvent} domain events to Kafka envelope JSON strings.
 *
 * <p>Envelope format:
 * <pre>{
 *   "eventId": "UUID",
 *   "eventType": "ORDER_CREATED",
 *   "timestamp": "ISO-8601",
 *   "source": "order-service",
 *   "correlationId": "UUID",
 *   "payload": { ... }
 * }</pre>
 */
@Component
public class OrderEventMapper {

    private final ObjectMapper objectMapper;

    public OrderEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converts an {@link OrderCreatedEvent} to a Kafka envelope JSON string.
     *
     * @param event the domain event
     * @return JSON string in envelope format
     * @throws IllegalStateException if JSON serialization fails
     */
    public String toJson(OrderCreatedEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "ORDER_CREATED");
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("source", "order-service");
        envelope.put("correlationId", UUID.randomUUID().toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", event.orderId());
        payload.put("customerId", event.customerId());
        payload.put("productId", event.productId());
        payload.put("quantity", event.quantity());

        Map<String, Object> totalAmount = new LinkedHashMap<>();
        totalAmount.put("amount", Double.parseDouble(event.totalAmount()));
        totalAmount.put("currency", event.currency());
        payload.put("totalAmount", totalAmount);

        envelope.put("payload", payload);

        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize OrderCreatedEvent to JSON", e);
        }
    }
}
