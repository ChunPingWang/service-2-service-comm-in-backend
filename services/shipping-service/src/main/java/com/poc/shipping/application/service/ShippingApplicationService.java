package com.poc.shipping.application.service;

import com.poc.shipping.application.port.in.ArrangeShipmentUseCase;
import com.poc.shipping.application.port.in.QueryShipmentUseCase;
import com.poc.shipping.application.port.out.ShipmentEventPort;
import com.poc.shipping.application.port.out.ShipmentRepository;
import com.poc.shipping.domain.event.ShipmentArrangedEvent;
import com.poc.shipping.domain.model.OrderId;
import com.poc.shipping.domain.model.Shipment;
import com.poc.shipping.domain.model.ShipmentId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Application service that orchestrates shipment creation and tracking.
 *
 * <p>Implements {@link ArrangeShipmentUseCase} and {@link QueryShipmentUseCase}
 * inbound ports, delegating persistence to the {@link ShipmentRepository}
 * and event publishing to the {@link ShipmentEventPort} outbound ports.</p>
 */
@Service
public class ShippingApplicationService implements ArrangeShipmentUseCase, QueryShipmentUseCase {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventPort shipmentEventPort;

    public ShippingApplicationService(ShipmentRepository shipmentRepository,
                                      ShipmentEventPort shipmentEventPort) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentEventPort = shipmentEventPort;
    }

    /**
     * Arranges a shipment by:
     * <ol>
     *   <li>Generating a unique ShipmentId</li>
     *   <li>Creating a PENDING shipment via the domain factory</li>
     *   <li>Saving the initial PENDING shipment</li>
     *   <li>Generating a tracking number</li>
     *   <li>Shipping the shipment (PENDING to IN_TRANSIT)</li>
     *   <li>Saving the IN_TRANSIT shipment</li>
     *   <li>Publishing a ShipmentArrangedEvent</li>
     * </ol>
     *
     * @param orderId the order identifier to ship
     * @return the shipped Shipment in IN_TRANSIT status
     */
    @Override
    public Shipment arrangeShipment(String orderId) {
        // 1. Generate a unique shipment identifier
        ShipmentId shipmentId = new ShipmentId(UUID.randomUUID().toString());

        // 2. Create PENDING shipment
        OrderId orderIdValue = new OrderId(orderId);
        Shipment pendingShipment = Shipment.create(shipmentId, orderIdValue);

        // 3. Save initial PENDING shipment
        shipmentRepository.save(pendingShipment);

        // 4. Generate tracking number
        String trackingNumber = "TRK-%d-%d".formatted(
                System.currentTimeMillis(),
                new Random().nextInt(10000));

        // 5. Ship the shipment (PENDING to IN_TRANSIT)
        Shipment shippedShipment = pendingShipment.ship(trackingNumber);

        // 6. Save IN_TRANSIT shipment
        shipmentRepository.save(shippedShipment);

        // 7. Publish ShipmentArrangedEvent
        ShipmentArrangedEvent event = new ShipmentArrangedEvent(
                shipmentId.id(),
                orderId,
                trackingNumber,
                "IN_TRANSIT",
                Instant.now()
        );
        shipmentEventPort.publish(event);

        // 8. Return the shipped shipment
        return shippedShipment;
    }

    @Override
    public Optional<Shipment> findById(ShipmentId shipmentId) {
        return shipmentRepository.findById(shipmentId);
    }
}
