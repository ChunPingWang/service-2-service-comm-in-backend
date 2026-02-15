package com.poc.order.adapter.out.messaging;

import com.poc.order.application.port.out.OrderEventPort;
import com.poc.order.domain.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka-based implementation of {@link OrderEventPort}.
 *
 * <p>Publishes {@link OrderCreatedEvent} domain events to the {@code order.created}
 * Kafka topic using the orderId as the message key for partition affinity.</p>
 */
@Component
public class OrderEventPublisher implements OrderEventPort {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final String TOPIC = "order.created";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OrderEventMapper orderEventMapper;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                               OrderEventMapper orderEventMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderEventMapper = orderEventMapper;
    }

    @Override
    public void publish(OrderCreatedEvent event) {
        String json = orderEventMapper.toJson(event);
        String key = event.orderId();

        log.info("Publishing OrderCreatedEvent to topic '{}' with key '{}'", TOPIC, key);
        kafkaTemplate.send(TOPIC, key, json);
        log.debug("OrderCreatedEvent published successfully: {}", json);
    }
}
