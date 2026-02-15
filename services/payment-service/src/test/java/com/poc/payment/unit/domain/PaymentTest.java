package com.poc.payment.unit.domain;

import com.poc.payment.domain.model.Money;
import com.poc.payment.domain.model.OrderId;
import com.poc.payment.domain.model.Payment;
import com.poc.payment.domain.model.PaymentId;
import com.poc.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTest {

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private static final PaymentId VALID_PAYMENT_ID = new PaymentId("pay-001");
    private static final OrderId VALID_ORDER_ID = new OrderId("ord-001");
    private static final Money VALID_AMOUNT = new Money(new BigDecimal("99.99"), "USD");

    // ── Payment.create() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Payment.create()")
    class Create {

        @Test
        @DisplayName("creates payment in PENDING status with correct fields and null completedAt")
        void createsPaymentInPendingStatus() {
            Payment payment = Payment.create(VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT);

            assertEquals(VALID_PAYMENT_ID, payment.paymentId());
            assertEquals(VALID_ORDER_ID, payment.orderId());
            assertEquals(VALID_AMOUNT, payment.amount());
            assertEquals(PaymentStatus.PENDING, payment.status());
            assertNotNull(payment.createdAt());
            assertNull(payment.completedAt());
        }

        @Test
        @DisplayName("fails with null paymentId")
        void failsWithNullPaymentId() {
            assertThrows(IllegalArgumentException.class,
                    () -> Payment.create(null, VALID_ORDER_ID, VALID_AMOUNT));
        }

        @Test
        @DisplayName("fails with null orderId")
        void failsWithNullOrderId() {
            assertThrows(IllegalArgumentException.class,
                    () -> Payment.create(VALID_PAYMENT_ID, null, VALID_AMOUNT));
        }

        @Test
        @DisplayName("fails with null amount")
        void failsWithNullAmount() {
            assertThrows(IllegalArgumentException.class,
                    () -> Payment.create(VALID_PAYMENT_ID, VALID_ORDER_ID, null));
        }

        @Test
        @DisplayName("fails with zero amount")
        void failsWithZeroAmount() {
            Money zeroAmount = new Money(BigDecimal.ZERO, "USD");
            assertThrows(IllegalArgumentException.class,
                    () -> Payment.create(VALID_PAYMENT_ID, VALID_ORDER_ID, zeroAmount));
        }

        @Test
        @DisplayName("fails with negative amount (rejected at Money level)")
        void failsWithNegativeAmount() {
            // Negative amounts are rejected by Money's own validation
            assertThrows(IllegalArgumentException.class,
                    () -> new Money(new BigDecimal("-1.00"), "USD"));
        }
    }

    // ── Payment.complete() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Payment.complete()")
    class Complete {

        @Test
        @DisplayName("transitions from PENDING to COMPLETED and sets completedAt")
        void transitionsFromPendingToCompleted() {
            Payment pending = Payment.create(VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT);

            Payment completed = pending.complete();

            assertEquals(PaymentStatus.COMPLETED, completed.status());
            assertNotNull(completed.completedAt());
            assertEquals(pending.paymentId(), completed.paymentId());
            assertEquals(pending.orderId(), completed.orderId());
            assertEquals(pending.amount(), completed.amount());
            assertEquals(pending.createdAt(), completed.createdAt());
        }

        @Test
        @DisplayName("fails from COMPLETED status")
        void failsFromCompletedStatus() {
            Payment completed = Payment.create(VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT)
                    .complete();

            assertThrows(IllegalStateException.class, completed::complete);
        }

        @Test
        @DisplayName("fails from FAILED status")
        void failsFromFailedStatus() {
            Payment failed = Payment.create(VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT)
                    .fail();

            assertThrows(IllegalStateException.class, failed::complete);
        }
    }

    // ── Payment.fail() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Payment.fail()")
    class Fail {

        @Test
        @DisplayName("transitions from PENDING to FAILED and sets completedAt")
        void transitionsFromPendingToFailed() {
            Payment pending = Payment.create(VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT);

            Payment failed = pending.fail();

            assertEquals(PaymentStatus.FAILED, failed.status());
            assertNotNull(failed.completedAt());
            assertEquals(pending.paymentId(), failed.paymentId());
            assertEquals(pending.orderId(), failed.orderId());
            assertEquals(pending.amount(), failed.amount());
            assertEquals(pending.createdAt(), failed.createdAt());
        }

        @Test
        @DisplayName("fails from COMPLETED status")
        void failsFromCompletedStatus() {
            Payment completed = Payment.create(VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT)
                    .complete();

            assertThrows(IllegalStateException.class, completed::fail);
        }

        @Test
        @DisplayName("fails from FAILED status")
        void failsFromFailedStatus() {
            Payment failed = Payment.create(VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT)
                    .fail();

            assertThrows(IllegalStateException.class, failed::fail);
        }
    }

    // ── Record validation ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Record validation")
    class RecordValidation {

        @Test
        @DisplayName("PENDING payment must not have completedAt")
        void pendingPaymentMustNotHaveCompletedAt() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Payment(VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT,
                            PaymentStatus.PENDING, Instant.now(), Instant.now()));
        }

        @Test
        @DisplayName("COMPLETED payment must have completedAt")
        void completedPaymentMustHaveCompletedAt() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Payment(VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT,
                            PaymentStatus.COMPLETED, Instant.now(), null));
        }

        @Test
        @DisplayName("FAILED payment must have completedAt")
        void failedPaymentMustHaveCompletedAt() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Payment(VALID_PAYMENT_ID, VALID_ORDER_ID, VALID_AMOUNT,
                            PaymentStatus.FAILED, Instant.now(), null));
        }
    }

    // ── PaymentId ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PaymentId")
    class PaymentIdTest {

        @Test
        @DisplayName("validates non-blank id")
        void validatesNonBlankId() {
            assertThrows(IllegalArgumentException.class, () -> new PaymentId(null));
            assertThrows(IllegalArgumentException.class, () -> new PaymentId(""));
            assertThrows(IllegalArgumentException.class, () -> new PaymentId("   "));
        }
    }

    // ── OrderId ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("OrderId")
    class OrderIdTest {

        @Test
        @DisplayName("validates non-blank id")
        void validatesNonBlankId() {
            assertThrows(IllegalArgumentException.class, () -> new OrderId(null));
            assertThrows(IllegalArgumentException.class, () -> new OrderId(""));
            assertThrows(IllegalArgumentException.class, () -> new OrderId("   "));
        }
    }

    // ── PaymentStatus ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PaymentStatus")
    class PaymentStatusTest {

        @Test
        @DisplayName("has values PENDING, COMPLETED, FAILED")
        void hasExpectedValues() {
            PaymentStatus[] values = PaymentStatus.values();

            assertEquals(3, values.length);
            assertEquals(PaymentStatus.PENDING, PaymentStatus.valueOf("PENDING"));
            assertEquals(PaymentStatus.COMPLETED, PaymentStatus.valueOf("COMPLETED"));
            assertEquals(PaymentStatus.FAILED, PaymentStatus.valueOf("FAILED"));
        }
    }

    // ── Money ────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Money")
    class MoneyTest {

        @Test
        @DisplayName("validates non-null amount")
        void validatesNonNullAmount() {
            assertThrows(IllegalArgumentException.class, () -> new Money(null, "USD"));
        }

        @Test
        @DisplayName("validates non-negative amount")
        void validatesNonNegativeAmount() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Money(new BigDecimal("-0.01"), "USD"));
        }

        @Test
        @DisplayName("allows zero amount")
        void allowsZeroAmount() {
            Money zero = new Money(BigDecimal.ZERO, "USD");
            assertEquals(BigDecimal.ZERO, zero.amount());
        }

        @Test
        @DisplayName("validates non-null currency")
        void validatesNonNullCurrency() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Money(BigDecimal.TEN, null));
        }

        @Test
        @DisplayName("validates 3-character currency code")
        void validates3CharCurrency() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Money(BigDecimal.TEN, "US"));
            assertThrows(IllegalArgumentException.class,
                    () -> new Money(BigDecimal.TEN, "USDD"));
            assertThrows(IllegalArgumentException.class,
                    () -> new Money(BigDecimal.TEN, ""));
        }
    }
}
