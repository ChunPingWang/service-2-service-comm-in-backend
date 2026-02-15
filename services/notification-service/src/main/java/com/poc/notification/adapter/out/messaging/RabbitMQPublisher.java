package com.poc.notification.adapter.out.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Outbound messaging adapter that publishes shipping notification messages to RabbitMQ.
 *
 * <p>Sends JSON messages to the {@code shipping.exchange} with routing key
 * {@code shipping.notification}, which routes them to the {@code shipping.queue}.</p>
 */
@Component
public class RabbitMQPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQPublisher.class);

    private static final String EXCHANGE = "shipping.exchange";
    private static final String ROUTING_KEY = "shipping.notification";

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQMessageMapper messageMapper;

    public RabbitMQPublisher(RabbitTemplate rabbitTemplate, RabbitMQMessageMapper messageMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageMapper = messageMapper;
    }

    /**
     * Publishes a shipping notification message for the given order.
     *
     * @param orderId the order identifier to notify shipping about
     */
    public void publishShippingNotification(String orderId) {
        String message = messageMapper.toShippingNotificationJson(orderId);
        log.info("Publishing shipping notification to RabbitMQ: exchange={}, routingKey={}, orderId={}",
                EXCHANGE, ROUTING_KEY, orderId);
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message);
    }
}
