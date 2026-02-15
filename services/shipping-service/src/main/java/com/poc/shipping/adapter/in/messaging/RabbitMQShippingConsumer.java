package com.poc.shipping.adapter.in.messaging;

import com.poc.shipping.application.port.in.ArrangeShipmentUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Inbound messaging adapter that consumes shipping notification messages from RabbitMQ.
 *
 * <p>Listens on the {@code shipping.queue} and delegates to
 * {@link ArrangeShipmentUseCase} to arrange shipment for the received order.</p>
 */
@Component
public class RabbitMQShippingConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQShippingConsumer.class);

    private final ArrangeShipmentUseCase arrangeShipmentUseCase;
    private final ShippingMessageMapper shippingMessageMapper;

    public RabbitMQShippingConsumer(ArrangeShipmentUseCase arrangeShipmentUseCase,
                                    ShippingMessageMapper shippingMessageMapper) {
        this.arrangeShipmentUseCase = arrangeShipmentUseCase;
        this.shippingMessageMapper = shippingMessageMapper;
    }

    /**
     * Consumes a shipping notification message from RabbitMQ and arranges a shipment.
     *
     * @param message the raw RabbitMQ message body (JSON string)
     */
    @RabbitListener(queues = "shipping.queue")
    public void consume(String message) {
        log.info("Received shipping notification from RabbitMQ");
        try {
            String orderId = shippingMessageMapper.extractOrderId(message);
            arrangeShipmentUseCase.arrangeShipment(orderId);
            log.info("Successfully arranged shipment for order {}", orderId);
        } catch (Exception e) {
            log.error("Failed to process shipping notification", e);
            throw e;
        }
    }
}
