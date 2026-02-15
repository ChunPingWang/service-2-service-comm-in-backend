package com.poc.payment.adapter.out.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.poc.payment.domain.event.PaymentCompletedEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps {@link PaymentCompletedEvent} domain events to Kafka envelope JSON strings.
 *
 * <p>Envelope format:
 * <pre>{
 *   "eventId": "UUID",
 *   "eventType": "PAYMENT_COMPLETED",
 *   "timestamp": "ISO-8601",
 *   "source": "payment-service",
 *   "correlationId": "UUID",
 *   "payload": { ... }
 * }</pre>
 */
@Component
public class PaymentEventMapper {

    private final ObjectMapper objectMapper;

    public PaymentEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converts a {@link PaymentCompletedEvent} to a Kafka envelope JSON string.
     *
     * @param event the domain event
     * @return JSON string in envelope format
     * @throws IllegalStateException if JSON serialization fails
     */
    public String toJson(PaymentCompletedEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "PAYMENT_COMPLETED");
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("source", "payment-service");
        envelope.put("correlationId", UUID.randomUUID().toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", event.paymentId());
        payload.put("orderId", event.orderId());

        Map<String, Object> amount = new LinkedHashMap<>();
        amount.put("amount", Double.parseDouble(event.amount()));
        amount.put("currency", event.currency());
        payload.put("amount", amount);

        payload.put("status", "COMPLETED");

        envelope.put("payload", payload);

        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize PaymentCompletedEvent to JSON", e);
        }
    }
}
