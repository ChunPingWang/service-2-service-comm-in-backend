package com.poc.order.unit.domain;

import com.poc.order.domain.event.OrderCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderCreatedEventTest {

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private static final String VALID_ORDER_ID = "ORD-001";
    private static final String VALID_CUSTOMER_ID = "CUST-001";
    private static final String VALID_PRODUCT_ID = "PROD-001";
    private static final int VALID_QUANTITY = 2;
    private static final String VALID_TOTAL_AMOUNT = "99.99";
    private static final String VALID_CURRENCY = "USD";
    private static final Instant VALID_TIMESTAMP = Instant.now();

    private static OrderCreatedEvent validEvent() {
        return new OrderCreatedEvent(
                VALID_ORDER_ID, VALID_CUSTOMER_ID, VALID_PRODUCT_ID,
                VALID_QUANTITY, VALID_TOTAL_AMOUNT, VALID_CURRENCY, VALID_TIMESTAMP);
    }

    // ── Valid creation ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid creation")
    class ValidCreation {

        @Test
        @DisplayName("creates event with all fields")
        void creates_event_with_all_fields() {
            OrderCreatedEvent event = validEvent();

            assertEquals(VALID_ORDER_ID, event.orderId());
            assertEquals(VALID_CUSTOMER_ID, event.customerId());
            assertEquals(VALID_PRODUCT_ID, event.productId());
            assertEquals(VALID_QUANTITY, event.quantity());
            assertEquals(VALID_TOTAL_AMOUNT, event.totalAmount());
            assertEquals(VALID_CURRENCY, event.currency());
            assertNotNull(event.timestamp());
        }
    }

    // ── orderId validation ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("orderId validation")
    class OrderIdValidation {

        @Test
        @DisplayName("fails with null orderId")
        void fails_with_null_order_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(null, VALID_CUSTOMER_ID, VALID_PRODUCT_ID,
                            VALID_QUANTITY, VALID_TOTAL_AMOUNT, VALID_CURRENCY, VALID_TIMESTAMP));
        }

        @Test
        @DisplayName("fails with blank orderId")
        void fails_with_blank_order_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent("", VALID_CUSTOMER_ID, VALID_PRODUCT_ID,
                            VALID_QUANTITY, VALID_TOTAL_AMOUNT, VALID_CURRENCY, VALID_TIMESTAMP));
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent("   ", VALID_CUSTOMER_ID, VALID_PRODUCT_ID,
                            VALID_QUANTITY, VALID_TOTAL_AMOUNT, VALID_CURRENCY, VALID_TIMESTAMP));
        }
    }

    // ── customerId validation ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("customerId validation")
    class CustomerIdValidation {

        @Test
        @DisplayName("fails with null customerId")
        void fails_with_null_customer_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, null, VALID_PRODUCT_ID,
                            VALID_QUANTITY, VALID_TOTAL_AMOUNT, VALID_CURRENCY, VALID_TIMESTAMP));
        }

        @Test
        @DisplayName("fails with blank customerId")
        void fails_with_blank_customer_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, "", VALID_PRODUCT_ID,
                            VALID_QUANTITY, VALID_TOTAL_AMOUNT, VALID_CURRENCY, VALID_TIMESTAMP));
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, "   ", VALID_PRODUCT_ID,
                            VALID_QUANTITY, VALID_TOTAL_AMOUNT, VALID_CURRENCY, VALID_TIMESTAMP));
        }
    }

    // ── productId validation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("productId validation")
    class ProductIdValidation {

        @Test
        @DisplayName("fails with null productId")
        void fails_with_null_product_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, VALID_CUSTOMER_ID, null,
                            VALID_QUANTITY, VALID_TOTAL_AMOUNT, VALID_CURRENCY, VALID_TIMESTAMP));
        }

        @Test
        @DisplayName("fails with blank productId")
        void fails_with_blank_product_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, VALID_CUSTOMER_ID, "",
                            VALID_QUANTITY, VALID_TOTAL_AMOUNT, VALID_CURRENCY, VALID_TIMESTAMP));
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, VALID_CUSTOMER_ID, "   ",
                            VALID_QUANTITY, VALID_TOTAL_AMOUNT, VALID_CURRENCY, VALID_TIMESTAMP));
        }
    }

    // ── quantity validation ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("quantity validation")
    class QuantityValidation {

        @Test
        @DisplayName("fails with quantity less than 1")
        void fails_with_quantity_less_than_one() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, VALID_CUSTOMER_ID, VALID_PRODUCT_ID,
                            0, VALID_TOTAL_AMOUNT, VALID_CURRENCY, VALID_TIMESTAMP));
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, VALID_CUSTOMER_ID, VALID_PRODUCT_ID,
                            -1, VALID_TOTAL_AMOUNT, VALID_CURRENCY, VALID_TIMESTAMP));
        }
    }

    // ── totalAmount validation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("totalAmount validation")
    class TotalAmountValidation {

        @Test
        @DisplayName("fails with null totalAmount")
        void fails_with_null_total_amount() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, VALID_CUSTOMER_ID, VALID_PRODUCT_ID,
                            VALID_QUANTITY, null, VALID_CURRENCY, VALID_TIMESTAMP));
        }

        @Test
        @DisplayName("fails with blank totalAmount")
        void fails_with_blank_total_amount() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, VALID_CUSTOMER_ID, VALID_PRODUCT_ID,
                            VALID_QUANTITY, "", VALID_CURRENCY, VALID_TIMESTAMP));
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, VALID_CUSTOMER_ID, VALID_PRODUCT_ID,
                            VALID_QUANTITY, "   ", VALID_CURRENCY, VALID_TIMESTAMP));
        }
    }

    // ── currency validation ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("currency validation")
    class CurrencyValidation {

        @Test
        @DisplayName("fails with null currency")
        void fails_with_null_currency() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, VALID_CUSTOMER_ID, VALID_PRODUCT_ID,
                            VALID_QUANTITY, VALID_TOTAL_AMOUNT, null, VALID_TIMESTAMP));
        }

        @Test
        @DisplayName("fails with blank currency")
        void fails_with_blank_currency() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, VALID_CUSTOMER_ID, VALID_PRODUCT_ID,
                            VALID_QUANTITY, VALID_TOTAL_AMOUNT, "", VALID_TIMESTAMP));
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, VALID_CUSTOMER_ID, VALID_PRODUCT_ID,
                            VALID_QUANTITY, VALID_TOTAL_AMOUNT, "   ", VALID_TIMESTAMP));
        }
    }

    // ── timestamp validation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("timestamp validation")
    class TimestampValidation {

        @Test
        @DisplayName("fails with null timestamp")
        void fails_with_null_timestamp() {
            assertThrows(IllegalArgumentException.class, () ->
                    new OrderCreatedEvent(VALID_ORDER_ID, VALID_CUSTOMER_ID, VALID_PRODUCT_ID,
                            VALID_QUANTITY, VALID_TOTAL_AMOUNT, VALID_CURRENCY, null));
        }
    }
}
