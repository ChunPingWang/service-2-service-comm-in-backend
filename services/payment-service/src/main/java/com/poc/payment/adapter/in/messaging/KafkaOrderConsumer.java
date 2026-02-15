package com.poc.payment.adapter.in.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.poc.payment.application.OrderCreatedPayload;
import com.poc.payment.application.port.in.HandleOrderCreatedUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that listens to the {@code order.created} topic and
 * delegates to the {@link HandleOrderCreatedUseCase} inbound port.
 *
 * <p>Uses manual acknowledgment to ensure at-least-once delivery semantics.</p>
 */
@Component
public class KafkaOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderConsumer.class);

    private final HandleOrderCreatedUseCase handleOrderCreatedUseCase;
    private final ObjectMapper objectMapper;

    public KafkaOrderConsumer(HandleOrderCreatedUseCase handleOrderCreatedUseCase,
                              ObjectMapper objectMapper) {
        this.handleOrderCreatedUseCase = handleOrderCreatedUseCase;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes an order-created event from the {@code order.created} topic,
     * extracts the payload, maps it to an {@link OrderCreatedPayload}, and
     * delegates processing to the use case.
     *
     * @param message the raw JSON message from Kafka
     * @param ack     the acknowledgment handle for manual commit
     */
    @KafkaListener(topics = "order.created", groupId = "payment-service")
    public void onOrderCreated(String message, Acknowledgment ack) {
        log.info("Received order.created event: {}", message);

        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.get("payload");

            String orderId = payload.get("orderId").asText();
            String productId = payload.get("productId").asText();
            int quantity = payload.get("quantity").asInt();

            JsonNode totalAmount = payload.get("totalAmount");
            String amount = totalAmount.get("amount").asText();
            String currency = totalAmount.get("currency").asText();

            OrderCreatedPayload orderPayload = new OrderCreatedPayload(
                    orderId, productId, quantity, amount, currency);

            handleOrderCreatedUseCase.handleOrderCreated(orderPayload);

            ack.acknowledge();
            log.info("Successfully processed order.created event for orderId: {}", orderId);

        } catch (JacksonException e) {
            log.error("Failed to parse order.created event: {}", message, e);
            throw new IllegalStateException("Failed to parse order.created event", e);
        }
    }
}
