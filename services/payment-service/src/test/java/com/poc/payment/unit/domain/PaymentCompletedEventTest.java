package com.poc.payment.unit.domain;

import com.poc.payment.domain.event.PaymentCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentCompletedEventTest {

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private static final String VALID_PAYMENT_ID = "PAY-001";
    private static final String VALID_ORDER_ID = "ORD-001";
    private static final String VALID_AMOUNT = "99.99";
    private static final String VALID_CURRENCY = "USD";
    private static final Instant VALID_TIMESTAMP = Instant.now();

    private static PaymentCompletedEvent validEvent() {
        return new PaymentCompletedEvent(
                VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT,
                VALID_CURRENCY, VALID_TIMESTAMP);
    }

    // ── Valid creation ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid creation")
    class ValidCreation {

        @Test
        @DisplayName("creates event with all fields")
        void creates_event_with_all_fields() {
            PaymentCompletedEvent event = validEvent();

            assertEquals(VALID_PAYMENT_ID, event.paymentId());
            assertEquals(VALID_ORDER_ID, event.orderId());
            assertEquals(VALID_AMOUNT, event.amount());
            assertEquals(VALID_CURRENCY, event.currency());
            assertNotNull(event.timestamp());
        }
    }

    // ── paymentId validation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("paymentId validation")
    class PaymentIdValidation {

        @Test
        @DisplayName("fails with null paymentId")
        void fails_with_null_payment_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    new PaymentCompletedEvent(null, VALID_ORDER_ID, VALID_AMOUNT,
                            VALID_CURRENCY, VALID_TIMESTAMP));
        }

        @Test
        @DisplayName("fails with blank paymentId")
        void fails_with_blank_payment_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    new PaymentCompletedEvent("", VALID_ORDER_ID, VALID_AMOUNT,
                            VALID_CURRENCY, VALID_TIMESTAMP));
            assertThrows(IllegalArgumentException.class, () ->
                    new PaymentCompletedEvent("   ", VALID_ORDER_ID, VALID_AMOUNT,
                            VALID_CURRENCY, VALID_TIMESTAMP));
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
                    new PaymentCompletedEvent(VALID_PAYMENT_ID, null, VALID_AMOUNT,
                            VALID_CURRENCY, VALID_TIMESTAMP));
        }

        @Test
        @DisplayName("fails with blank orderId")
        void fails_with_blank_order_id() {
            assertThrows(IllegalArgumentException.class, () ->
                    new PaymentCompletedEvent(VALID_PAYMENT_ID, "", VALID_AMOUNT,
                            VALID_CURRENCY, VALID_TIMESTAMP));
            assertThrows(IllegalArgumentException.class, () ->
                    new PaymentCompletedEvent(VALID_PAYMENT_ID, "   ", VALID_AMOUNT,
                            VALID_CURRENCY, VALID_TIMESTAMP));
        }
    }

    // ── amount validation ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("amount validation")
    class AmountValidation {

        @Test
        @DisplayName("fails with null amount")
        void fails_with_null_amount() {
            assertThrows(IllegalArgumentException.class, () ->
                    new PaymentCompletedEvent(VALID_PAYMENT_ID, VALID_ORDER_ID, null,
                            VALID_CURRENCY, VALID_TIMESTAMP));
        }

        @Test
        @DisplayName("fails with blank amount")
        void fails_with_blank_amount() {
            assertThrows(IllegalArgumentException.class, () ->
                    new PaymentCompletedEvent(VALID_PAYMENT_ID, VALID_ORDER_ID, "",
                            VALID_CURRENCY, VALID_TIMESTAMP));
            assertThrows(IllegalArgumentException.class, () ->
                    new PaymentCompletedEvent(VALID_PAYMENT_ID, VALID_ORDER_ID, "   ",
                            VALID_CURRENCY, VALID_TIMESTAMP));
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
                    new PaymentCompletedEvent(VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT,
                            null, VALID_TIMESTAMP));
        }

        @Test
        @DisplayName("fails with blank currency")
        void fails_with_blank_currency() {
            assertThrows(IllegalArgumentException.class, () ->
                    new PaymentCompletedEvent(VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT,
                            "", VALID_TIMESTAMP));
            assertThrows(IllegalArgumentException.class, () ->
                    new PaymentCompletedEvent(VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT,
                            "   ", VALID_TIMESTAMP));
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
                    new PaymentCompletedEvent(VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT,
                            VALID_CURRENCY, null));
        }
    }
}
