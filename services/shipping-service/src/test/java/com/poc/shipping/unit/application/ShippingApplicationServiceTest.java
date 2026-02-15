package com.poc.shipping.unit.application;

import com.poc.shipping.application.port.out.ShipmentEventPort;
import com.poc.shipping.application.port.out.ShipmentRepository;
import com.poc.shipping.application.service.ShippingApplicationService;
import com.poc.shipping.domain.event.ShipmentArrangedEvent;
import com.poc.shipping.domain.model.OrderId;
import com.poc.shipping.domain.model.Shipment;
import com.poc.shipping.domain.model.ShipmentId;
import com.poc.shipping.domain.model.ShipmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingApplicationServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentEventPort shipmentEventPort;

    private ShippingApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ShippingApplicationService(shipmentRepository, shipmentEventPort);
    }

    // -- arrangeShipment ---------------------------------------------------------

    @Nested
    @DisplayName("arrangeShipment")
    class ArrangeShipment {

        @Test
        @DisplayName("creates PENDING shipment, ships with tracking number to IN_TRANSIT, saves twice")
        void arrangeShipment_success() {
            // Arrange: mock repository to return whatever is passed in
            when(shipmentRepository.save(any(Shipment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Shipment result = service.arrangeShipment("ord-001");

            // Assert: final shipment is IN_TRANSIT
            assertNotNull(result);
            assertEquals(ShipmentStatus.IN_TRANSIT, result.status());
            assertEquals(new OrderId("ord-001"), result.orderId());
            assertNotNull(result.trackingNumber());
            assertTrue(result.trackingNumber().startsWith("TRK-"));

            // Verify repository was called twice (once for PENDING, once for IN_TRANSIT)
            ArgumentCaptor<Shipment> captor = ArgumentCaptor.forClass(Shipment.class);
            verify(shipmentRepository, times(2)).save(captor.capture());

            Shipment firstSave = captor.getAllValues().get(0);
            Shipment secondSave = captor.getAllValues().get(1);

            assertEquals(ShipmentStatus.PENDING, firstSave.status());
            assertEquals(ShipmentStatus.IN_TRANSIT, secondSave.status());
        }

        @Test
        @DisplayName("saves shipment with correct fields")
        void arrangeShipment_savesShipment() {
            // Arrange
            when(shipmentRepository.save(any(Shipment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Shipment result = service.arrangeShipment("ord-002");

            // Assert: verify repository.save called with correct fields
            ArgumentCaptor<Shipment> captor = ArgumentCaptor.forClass(Shipment.class);
            verify(shipmentRepository, times(2)).save(captor.capture());

            Shipment firstSave = captor.getAllValues().get(0);
            assertEquals(new OrderId("ord-002"), firstSave.orderId());
            assertEquals(ShipmentStatus.PENDING, firstSave.status());
            assertNotNull(firstSave.shipmentId());
            assertNotNull(firstSave.createdAt());

            Shipment secondSave = captor.getAllValues().get(1);
            assertEquals(ShipmentStatus.IN_TRANSIT, secondSave.status());
            assertEquals(new OrderId("ord-002"), secondSave.orderId());
            assertTrue(secondSave.trackingNumber().startsWith("TRK-"));
        }

        @Test
        @DisplayName("publishes ShipmentArrangedEvent after shipping")
        void arrangeShipment_publishesEvent() {
            // Arrange
            when(shipmentRepository.save(any(Shipment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Shipment result = service.arrangeShipment("ord-003");

            // Assert: verify event was published with correct fields
            ArgumentCaptor<ShipmentArrangedEvent> eventCaptor =
                    ArgumentCaptor.forClass(ShipmentArrangedEvent.class);
            verify(shipmentEventPort).publish(eventCaptor.capture());

            ShipmentArrangedEvent event = eventCaptor.getValue();
            assertNotNull(event.shipmentId());
            assertEquals("ord-003", event.orderId());
            assertTrue(event.trackingNumber().startsWith("TRK-"));
            assertEquals("IN_TRANSIT", event.status());
            assertNotNull(event.timestamp());
        }
    }

    // -- findById ----------------------------------------------------------------

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns shipment when found in repository")
        void findById_exists() {
            // Arrange
            ShipmentId shipmentId = new ShipmentId("ship-123");
            Shipment shipment = Shipment.create(
                    shipmentId,
                    new OrderId("ord-001")
            );
            when(shipmentRepository.findById(shipmentId)).thenReturn(Optional.of(shipment));

            // Act
            Optional<Shipment> result = service.findById(shipmentId);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(shipmentId, result.get().shipmentId());
            assertEquals(ShipmentStatus.PENDING, result.get().status());
            verify(shipmentRepository).findById(shipmentId);
        }

        @Test
        @DisplayName("returns empty Optional when shipment not found")
        void findById_notFound() {
            // Arrange
            ShipmentId shipmentId = new ShipmentId("ship-nonexistent");
            when(shipmentRepository.findById(shipmentId)).thenReturn(Optional.empty());

            // Act
            Optional<Shipment> result = service.findById(shipmentId);

            // Assert
            assertTrue(result.isEmpty());
            verify(shipmentRepository).findById(shipmentId);
        }
    }
}
