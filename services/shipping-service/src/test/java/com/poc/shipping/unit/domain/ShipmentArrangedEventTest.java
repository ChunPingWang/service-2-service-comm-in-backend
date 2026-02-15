package com.poc.shipping.unit.domain;

import com.poc.shipping.domain.event.ShipmentArrangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipmentArrangedEventTest {

    // -- Fixtures ---------------------------------------------------------------

    private static final String VALID_SHIPMENT_ID = "shp-001";
    private static final String VALID_ORDER_ID = "ord-001";
    private static final String VALID_TRACKING_NUMBER = "TRK-20260214-001";
    private static final String VALID_STATUS = "IN_TRANSIT";
    private static final Instant VALID_TIMESTAMP = Instant.now();

    private static ShipmentArrangedEvent validEvent() {
        return new ShipmentArrangedEvent(
                VALID_SHIPMENT_ID, VALID_ORDER_ID, VALID_TRACKING_NUMBER,
                VALID_STATUS, VALID_TIMESTAMP);
    }

    // -- Valid creation ----------------------------------------------------------

    @Nested
    @DisplayName("Valid creation")
    class ValidCreation {

        @Test
        @DisplayName("creates event with all fields")
        void creates_event_with_all_fields() {
            ShipmentArrangedEvent event = validEvent();

            assertEquals(VALID_SHIPMENT_ID, event.shipmentId());
            assertEquals(VALID_ORDER_ID, event.orderId());
            assertEquals(VALID_TRACKING_NUMBER, event.trackingNumber());
            assertEquals(VALID_STATUS, event.status());
            assertNotNull(event.timestamp());
        }
    }

    // -- shipmentId validation ---------------------------------------------------

    @Nested
    @DisplayName("shipmentId validation")
    class ShipmentIdValidation {

        @Test
        @DisplayName("fails with null shipmentId")
        void fails_with_null_shipment_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ShipmentArrangedEvent(null, VALID_ORDER_ID, VALID_TRACKING_NUMBER,
                            VALID_STATUS, VALID_TIMESTAMP));
        }

        @Test
        @DisplayName("fails with blank shipmentId")
        void fails_with_blank_shipment_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ShipmentArrangedEvent("", VALID_ORDER_ID, VALID_TRACKING_NUMBER,
                            VALID_STATUS, VALID_TIMESTAMP));
            assertThrows(IllegalArgumentException.class, () ->
                    new ShipmentArrangedEvent("   ", VALID_ORDER_ID, VALID_TRACKING_NUMBER,
                            VALID_STATUS, VALID_TIMESTAMP));
        }
    }

    // -- orderId validation ------------------------------------------------------

    @Nested
    @DisplayName("orderId validation")
    class OrderIdValidation {

        @Test
        @DisplayName("fails with null orderId")
        void fails_with_null_order_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ShipmentArrangedEvent(VALID_SHIPMENT_ID, null, VALID_TRACKING_NUMBER,
                            VALID_STATUS, VALID_TIMESTAMP));
        }

        @Test
        @DisplayName("fails with blank orderId")
        void fails_with_blank_order_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ShipmentArrangedEvent(VALID_SHIPMENT_ID, "", VALID_TRACKING_NUMBER,
                            VALID_STATUS, VALID_TIMESTAMP));
            assertThrows(IllegalArgumentException.class, () ->
                    new ShipmentArrangedEvent(VALID_SHIPMENT_ID, "   ", VALID_TRACKING_NUMBER,
                            VALID_STATUS, VALID_TIMESTAMP));
        }
    }

    // -- trackingNumber validation -----------------------------------------------

    @Nested
    @DisplayName("trackingNumber validation")
    class TrackingNumberValidation {

        @Test
        @DisplayName("fails with null trackingNumber")
        void fails_with_null_tracking_number() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ShipmentArrangedEvent(VALID_SHIPMENT_ID, VALID_ORDER_ID, null,
                            VALID_STATUS, VALID_TIMESTAMP));
        }

        @Test
        @DisplayName("fails with blank trackingNumber")
        void fails_with_blank_tracking_number() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ShipmentArrangedEvent(VALID_SHIPMENT_ID, VALID_ORDER_ID, "",
                            VALID_STATUS, VALID_TIMESTAMP));
            assertThrows(IllegalArgumentException.class, () ->
                    new ShipmentArrangedEvent(VALID_SHIPMENT_ID, VALID_ORDER_ID, "   ",
                            VALID_STATUS, VALID_TIMESTAMP));
        }
    }

    // -- status validation -------------------------------------------------------

    @Nested
    @DisplayName("status validation")
    class StatusValidation {

        @Test
        @DisplayName("fails with null status")
        void fails_with_null_status() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ShipmentArrangedEvent(VALID_SHIPMENT_ID, VALID_ORDER_ID, VALID_TRACKING_NUMBER,
                            null, VALID_TIMESTAMP));
        }

        @Test
        @DisplayName("fails with blank status")
        void fails_with_blank_status() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ShipmentArrangedEvent(VALID_SHIPMENT_ID, VALID_ORDER_ID, VALID_TRACKING_NUMBER,
                            "", VALID_TIMESTAMP));
            assertThrows(IllegalArgumentException.class, () ->
                    new ShipmentArrangedEvent(VALID_SHIPMENT_ID, VALID_ORDER_ID, VALID_TRACKING_NUMBER,
                            "   ", VALID_TIMESTAMP));
        }
    }

    // -- timestamp validation ----------------------------------------------------

    @Nested
    @DisplayName("timestamp validation")
    class TimestampValidation {

        @Test
        @DisplayName("fails with null timestamp")
        void fails_with_null_timestamp() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ShipmentArrangedEvent(VALID_SHIPMENT_ID, VALID_ORDER_ID, VALID_TRACKING_NUMBER,
                            VALID_STATUS, null));
        }
    }
}
