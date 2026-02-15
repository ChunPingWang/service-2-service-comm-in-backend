package com.poc.shipping.unit.domain;

import com.poc.shipping.domain.model.OrderId;
import com.poc.shipping.domain.model.Shipment;
import com.poc.shipping.domain.model.ShipmentId;
import com.poc.shipping.domain.model.ShipmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipmentTest {

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private static final ShipmentId VALID_SHIPMENT_ID = new ShipmentId("SHIP-001");
    private static final OrderId VALID_ORDER_ID = new OrderId("ORD-001");
    private static final String VALID_TRACKING_NUMBER = "TRACK-12345";

    private static Shipment validShipment() {
        return Shipment.create(VALID_SHIPMENT_ID, VALID_ORDER_ID);
    }

    // ── Shipment.create() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Shipment.create()")
    class Create {

        @Test
        @DisplayName("creates shipment in PENDING status with null trackingNumber")
        void creates_shipment_in_pending_status() {
            Shipment shipment = validShipment();

            assertEquals(VALID_SHIPMENT_ID, shipment.shipmentId());
            assertEquals(VALID_ORDER_ID, shipment.orderId());
            assertEquals(ShipmentStatus.PENDING, shipment.status());
            assertNull(shipment.trackingNumber());
            assertNotNull(shipment.createdAt());
        }

        @Test
        @DisplayName("fails with null shipmentId")
        void fails_with_null_shipment_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    Shipment.create(null, VALID_ORDER_ID));
        }

        @Test
        @DisplayName("fails with null orderId")
        void fails_with_null_order_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    Shipment.create(VALID_SHIPMENT_ID, null));
        }
    }

    // ── Shipment.ship() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Shipment.ship()")
    class Ship {

        @Test
        @DisplayName("transitions from PENDING to IN_TRANSIT with trackingNumber")
        void transitions_from_pending_to_in_transit() {
            Shipment pending = validShipment();

            Shipment inTransit = pending.ship(VALID_TRACKING_NUMBER);

            assertEquals(ShipmentStatus.IN_TRANSIT, inTransit.status());
            assertEquals(VALID_TRACKING_NUMBER, inTransit.trackingNumber());
            assertEquals(pending.shipmentId(), inTransit.shipmentId());
            assertEquals(pending.orderId(), inTransit.orderId());
            assertEquals(pending.createdAt(), inTransit.createdAt());
        }

        @Test
        @DisplayName("fails from IN_TRANSIT status")
        void fails_from_in_transit_status() {
            Shipment inTransit = validShipment().ship(VALID_TRACKING_NUMBER);

            assertThrows(IllegalStateException.class, () ->
                    inTransit.ship("TRACK-99999"));
        }

        @Test
        @DisplayName("fails from DELIVERED status")
        void fails_from_delivered_status() {
            Shipment delivered = validShipment()
                    .ship(VALID_TRACKING_NUMBER)
                    .deliver();

            assertThrows(IllegalStateException.class, () ->
                    delivered.ship("TRACK-99999"));
        }

        @Test
        @DisplayName("fails with null trackingNumber")
        void fails_with_null_tracking_number() {
            Shipment pending = validShipment();

            assertThrows(IllegalArgumentException.class, () ->
                    pending.ship(null));
        }

        @Test
        @DisplayName("fails with blank trackingNumber")
        void fails_with_blank_tracking_number() {
            Shipment pending = validShipment();

            assertThrows(IllegalArgumentException.class, () ->
                    pending.ship(""));
            assertThrows(IllegalArgumentException.class, () ->
                    pending.ship("   "));
        }
    }

    // ── Shipment.deliver() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Shipment.deliver()")
    class Deliver {

        @Test
        @DisplayName("transitions from IN_TRANSIT to DELIVERED")
        void transitions_from_in_transit_to_delivered() {
            Shipment inTransit = validShipment().ship(VALID_TRACKING_NUMBER);

            Shipment delivered = inTransit.deliver();

            assertEquals(ShipmentStatus.DELIVERED, delivered.status());
            assertEquals(VALID_TRACKING_NUMBER, delivered.trackingNumber());
            assertEquals(inTransit.shipmentId(), delivered.shipmentId());
            assertEquals(inTransit.orderId(), delivered.orderId());
            assertEquals(inTransit.createdAt(), delivered.createdAt());
        }

        @Test
        @DisplayName("fails from PENDING status")
        void fails_from_pending_status() {
            Shipment pending = validShipment();

            assertThrows(IllegalStateException.class, pending::deliver);
        }

        @Test
        @DisplayName("fails from DELIVERED status")
        void fails_from_delivered_status() {
            Shipment delivered = validShipment()
                    .ship(VALID_TRACKING_NUMBER)
                    .deliver();

            assertThrows(IllegalStateException.class, delivered::deliver);
        }
    }

    // ── Record validation ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Record validation")
    class RecordValidation {

        @Test
        @DisplayName("PENDING allows null trackingNumber")
        void pending_allows_null_tracking_number() {
            Shipment shipment = new Shipment(VALID_SHIPMENT_ID, VALID_ORDER_ID,
                    ShipmentStatus.PENDING, null, Instant.now());

            assertNull(shipment.trackingNumber());
        }

        @Test
        @DisplayName("IN_TRANSIT requires non-blank trackingNumber")
        void in_transit_requires_tracking_number() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Shipment(VALID_SHIPMENT_ID, VALID_ORDER_ID,
                            ShipmentStatus.IN_TRANSIT, null, Instant.now()));
            assertThrows(IllegalArgumentException.class, () ->
                    new Shipment(VALID_SHIPMENT_ID, VALID_ORDER_ID,
                            ShipmentStatus.IN_TRANSIT, "", Instant.now()));
            assertThrows(IllegalArgumentException.class, () ->
                    new Shipment(VALID_SHIPMENT_ID, VALID_ORDER_ID,
                            ShipmentStatus.IN_TRANSIT, "   ", Instant.now()));
        }

        @Test
        @DisplayName("DELIVERED requires non-blank trackingNumber")
        void delivered_requires_tracking_number() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Shipment(VALID_SHIPMENT_ID, VALID_ORDER_ID,
                            ShipmentStatus.DELIVERED, null, Instant.now()));
            assertThrows(IllegalArgumentException.class, () ->
                    new Shipment(VALID_SHIPMENT_ID, VALID_ORDER_ID,
                            ShipmentStatus.DELIVERED, "", Instant.now()));
        }

        @Test
        @DisplayName("fails with null createdAt")
        void fails_with_null_created_at() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Shipment(VALID_SHIPMENT_ID, VALID_ORDER_ID,
                            ShipmentStatus.PENDING, null, null));
        }
    }

    // ── ShipmentId ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ShipmentId")
    class ShipmentIdTest {

        @Test
        @DisplayName("validates non-blank id")
        void validates_non_blank_id() {
            assertThrows(IllegalArgumentException.class, () -> new ShipmentId(null));
            assertThrows(IllegalArgumentException.class, () -> new ShipmentId(""));
            assertThrows(IllegalArgumentException.class, () -> new ShipmentId("   "));
        }
    }

    // ── OrderId ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("OrderId")
    class OrderIdTest {

        @Test
        @DisplayName("validates non-blank id")
        void validates_non_blank_id() {
            assertThrows(IllegalArgumentException.class, () -> new OrderId(null));
            assertThrows(IllegalArgumentException.class, () -> new OrderId(""));
            assertThrows(IllegalArgumentException.class, () -> new OrderId("   "));
        }
    }

    // ── ShipmentStatus ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ShipmentStatus")
    class ShipmentStatusTest {

        @Test
        @DisplayName("has PENDING, IN_TRANSIT, DELIVERED values")
        void has_expected_values() {
            ShipmentStatus[] values = ShipmentStatus.values();

            assertEquals(3, values.length);
            assertEquals(ShipmentStatus.PENDING, ShipmentStatus.valueOf("PENDING"));
            assertEquals(ShipmentStatus.IN_TRANSIT, ShipmentStatus.valueOf("IN_TRANSIT"));
            assertEquals(ShipmentStatus.DELIVERED, ShipmentStatus.valueOf("DELIVERED"));
        }
    }
}
