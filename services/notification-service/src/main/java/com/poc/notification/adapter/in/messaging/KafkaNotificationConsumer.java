package com.poc.notification.adapter.in.messaging;

import com.poc.notification.adapter.out.messaging.RabbitMQPublisher;
import com.poc.notification.application.PaymentCompletedPayload;
import com.poc.notification.application.port.in.HandlePaymentEventUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Inbound messaging adapter that consumes payment-completed events from Kafka.
 *
 * <p>Listens on the {@code payment.completed} topic, parses the event envelope,
 * delegates to {@link HandlePaymentEventUseCase} for notification creation,
 * then publishes a shipping notification to RabbitMQ via {@link RabbitMQPublisher}.</p>
 *
 * <p>Uses manual acknowledgment to ensure messages are only acknowledged after
 * successful processing.</p>
 */
@Component
public class KafkaNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationConsumer.class);

    private final HandlePaymentEventUseCase handlePaymentEventUseCase;
    private final RabbitMQPublisher rabbitMQPublisher;
    private final KafkaEventMapper kafkaEventMapper;

    public KafkaNotificationConsumer(HandlePaymentEventUseCase handlePaymentEventUseCase,
                                     RabbitMQPublisher rabbitMQPublisher,
                                     KafkaEventMapper kafkaEventMapper) {
        this.handlePaymentEventUseCase = handlePaymentEventUseCase;
        this.rabbitMQPublisher = rabbitMQPublisher;
        this.kafkaEventMapper = kafkaEventMapper;
    }

    /**
     * Consumes a payment-completed event from Kafka, processes the notification,
     * and publishes a shipping notification to RabbitMQ.
     *
     * @param message the raw Kafka message value (JSON event envelope)
     * @param ack     the acknowledgment handle for manual commit
     */
    @KafkaListener(topics = "payment.completed", groupId = "notification-service")
    public void consume(String message, Acknowledgment ack) {
        log.info("Received payment.completed event from Kafka");
        try {
            // 1. Parse the Kafka event envelope and extract payload
            PaymentCompletedPayload payload = kafkaEventMapper.toPaymentCompletedPayload(message);

            // 2. Delegate to application service for notification creation
            handlePaymentEventUseCase.handlePaymentCompleted(payload);

            // 3. Publish shipping notification to RabbitMQ
            rabbitMQPublisher.publishShippingNotification(payload.orderId());

            // 4. Acknowledge the message after successful processing
            ack.acknowledge();
            log.info("Successfully processed payment.completed event for order {}", payload.orderId());
        } catch (Exception e) {
            log.error("Failed to process payment.completed event", e);
            throw e;
        }
    }
}
