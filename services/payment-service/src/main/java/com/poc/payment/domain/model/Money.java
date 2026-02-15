package com.poc.payment.domain.model;

import java.math.BigDecimal;

/**
 * Value object representing a monetary amount with currency.
 * This is a context-local copy for the Payment bounded context.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-character code");
        }
    }
}
