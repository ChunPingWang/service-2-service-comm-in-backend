package com.poc.order.adapter.in.messaging;

import com.poc.order.application.ShipmentArrangedPayload;
import com.poc.order.application.port.in.HandleShipmentEventUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that listens to the {@code shipment.arranged} topic and
 * delegates to the {@link HandleShipmentEventUseCase} inbound port.
 *
 * <p>Uses manual acknowledgment to ensure at-least-once delivery semantics.</p>
 */
@Component
public class KafkaShipmentConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaShipmentConsumer.class);

    private final HandleShipmentEventUseCase handleShipmentEventUseCase;
    private final ShipmentEventMapper shipmentEventMapper;

    public KafkaShipmentConsumer(HandleShipmentEventUseCase handleShipmentEventUseCase,
                                 ShipmentEventMapper shipmentEventMapper) {
        this.handleShipmentEventUseCase = handleShipmentEventUseCase;
        this.shipmentEventMapper = shipmentEventMapper;
    }

    /**
     * Consumes a shipment-arranged event from the {@code shipment.arranged} topic,
     * extracts the payload, and delegates processing to the use case.
     *
     * @param message the raw JSON message from Kafka
     * @param ack     the acknowledgment handle for manual commit
     */
    @KafkaListener(topics = "shipment.arranged", groupId = "order-service")
    public void onShipmentArranged(String message, Acknowledgment ack) {
        log.info("Received shipment.arranged event: {}", message);

        ShipmentArrangedPayload payload = shipmentEventMapper.fromJson(message);

        handleShipmentEventUseCase.handleShipmentArranged(payload);

        ack.acknowledge();
        log.info("Successfully processed shipment.arranged event for orderId: {}", payload.orderId());
    }
}
