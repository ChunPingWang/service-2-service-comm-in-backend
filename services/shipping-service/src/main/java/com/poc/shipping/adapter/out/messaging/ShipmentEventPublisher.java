package com.poc.shipping.adapter.out.messaging;

import com.poc.shipping.application.port.out.ShipmentEventPort;
import com.poc.shipping.domain.event.ShipmentArrangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka-based implementation of {@link ShipmentEventPort}.
 *
 * <p>Publishes {@link ShipmentArrangedEvent} domain events to the {@code shipment.arranged}
 * Kafka topic using the orderId as the message key for partition affinity.</p>
 */
@Component
public class ShipmentEventPublisher implements ShipmentEventPort {

    private static final Logger log = LoggerFactory.getLogger(ShipmentEventPublisher.class);
    private static final String TOPIC = "shipment.arranged";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ShipmentEventMapper shipmentEventMapper;

    public ShipmentEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                  ShipmentEventMapper shipmentEventMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.shipmentEventMapper = shipmentEventMapper;
    }

    @Override
    public void publish(ShipmentArrangedEvent event) {
        String json = shipmentEventMapper.toJson(event);
        String key = event.orderId();

        log.info("Publishing ShipmentArrangedEvent to topic '{}' with key '{}'", TOPIC, key);
        kafkaTemplate.send(TOPIC, key, json);
        log.debug("ShipmentArrangedEvent published successfully: {}", json);
    }
}
