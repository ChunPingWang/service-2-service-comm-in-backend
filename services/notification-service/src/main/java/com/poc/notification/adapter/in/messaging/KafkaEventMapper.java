package com.poc.notification.adapter.in.messaging;

import com.poc.notification.application.PaymentCompletedPayload;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Maps Kafka event envelope JSON strings to application-layer payload records.
 *
 * <p>Expects the Kafka event envelope format:
 * <pre>{@code
 * {
 *   "eventId": "UUID",
 *   "eventType": "PAYMENT_COMPLETED",
 *   "timestamp": "ISO-8601",
 *   "source": "payment-service",
 *   "correlationId": "UUID",
 *   "payload": {
 *     "paymentId": "pay-001",
 *     "orderId": "ord-001",
 *     "amount": { "amount": 59.98, "currency": "USD" },
 *     "status": "COMPLETED"
 *   }
 * }
 * }</pre>
 */
@Component
public class KafkaEventMapper {

    private final ObjectMapper objectMapper;

    public KafkaEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses a Kafka event envelope JSON string and extracts the payload
     * into a {@link PaymentCompletedPayload} record.
     *
     * @param json the raw Kafka message value (JSON envelope)
     * @return the extracted payment-completed payload
     * @throws IllegalArgumentException if the JSON cannot be parsed or required fields are missing
     */
    public PaymentCompletedPayload toPaymentCompletedPayload(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode payload = root.path("payload");

            String paymentId = payload.path("paymentId").asText();
            String orderId = payload.path("orderId").asText();

            JsonNode amountNode = payload.path("amount");
            String amount = amountNode.path("amount").asText();
            String currency = amountNode.path("currency").asText();

            return new PaymentCompletedPayload(paymentId, orderId, amount, currency);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to parse Kafka event envelope", e);
        }
    }
}
