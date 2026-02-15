package com.poc.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Aggregate root representing a payment in the system.
 *
 * <p>A payment starts in {@link PaymentStatus#PENDING} status and can transition
 * to either {@link PaymentStatus#COMPLETED} or {@link PaymentStatus#FAILED}.</p>
 */
public record Payment(
        PaymentId paymentId,
        OrderId orderId,
        Money amount,
        PaymentStatus status,
        Instant createdAt,
        Instant completedAt
) {

    public Payment {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId must not be null");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }

        // PENDING payments must not have a completedAt timestamp
        if (status == PaymentStatus.PENDING && completedAt != null) {
            throw new IllegalArgumentException("PENDING payment must not have completedAt");
        }

        // COMPLETED and FAILED payments must have a completedAt timestamp
        if ((status == PaymentStatus.COMPLETED || status == PaymentStatus.FAILED)
                && completedAt == null) {
            throw new IllegalArgumentException(status + " payment must have completedAt");
        }
    }

    /**
     * Factory method to create a new payment in PENDING status.
     *
     * @param paymentId unique payment identifier
     * @param orderId   the order this payment is for
     * @param amount    the monetary amount (must be strictly positive)
     * @return a new Payment in PENDING status
     */
    public static Payment create(PaymentId paymentId, OrderId orderId, Money amount) {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId must not be null");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (amount.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("payment amount must be strictly positive");
        }

        return new Payment(paymentId, orderId, amount, PaymentStatus.PENDING, Instant.now(), null);
    }

    /**
     * Transitions this payment from PENDING to COMPLETED.
     *
     * @return a new Payment with COMPLETED status and completedAt set
     * @throws IllegalStateException if the payment is not in PENDING status
     */
    public Payment complete() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot complete payment in " + status + " status; must be PENDING");
        }
        return new Payment(paymentId, orderId, amount, PaymentStatus.COMPLETED, createdAt, Instant.now());
    }

    /**
     * Transitions this payment from PENDING to FAILED.
     *
     * @return a new Payment with FAILED status and completedAt set
     * @throws IllegalStateException if the payment is not in PENDING status
     */
    public Payment fail() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot fail payment in " + status + " status; must be PENDING");
        }
        return new Payment(paymentId, orderId, amount, PaymentStatus.FAILED, createdAt, Instant.now());
    }
}
