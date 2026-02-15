package com.poc.payment.domain.model;

/**
 * Value object representing a unique payment identifier.
 */
public record PaymentId(String id) {

    public PaymentId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("paymentId must not be blank");
        }
    }
}
