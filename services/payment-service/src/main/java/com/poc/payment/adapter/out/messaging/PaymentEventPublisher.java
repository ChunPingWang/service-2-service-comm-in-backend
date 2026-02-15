package com.poc.payment.adapter.out.messaging;

import com.poc.payment.application.port.out.PaymentEventPort;
import com.poc.payment.domain.event.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka-based implementation of {@link PaymentEventPort}.
 *
 * <p>Publishes {@link PaymentCompletedEvent} domain events to the {@code payment.completed}
 * Kafka topic using the orderId as the message key for partition affinity.</p>
 */
@Component
public class PaymentEventPublisher implements PaymentEventPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    private static final String TOPIC = "payment.completed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PaymentEventMapper paymentEventMapper;

    public PaymentEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                 PaymentEventMapper paymentEventMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.paymentEventMapper = paymentEventMapper;
    }

    @Override
    public void publish(PaymentCompletedEvent event) {
        String json = paymentEventMapper.toJson(event);
        String key = event.orderId();

        log.info("Publishing PaymentCompletedEvent to topic '{}' with key '{}'", TOPIC, key);
        kafkaTemplate.send(TOPIC, key, json);
        log.debug("PaymentCompletedEvent published successfully: {}", json);
    }
}
