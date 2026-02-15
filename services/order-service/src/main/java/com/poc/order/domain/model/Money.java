package com.poc.order.domain.model;

import java.math.BigDecimal;

/**
 * Value object representing a monetary amount with currency.
 * Immutable. Validates non-null, non-negative amount and ISO 3-char currency code.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-character code");
        }
        currency = currency.toUpperCase();
    }

    /**
     * Adds another Money of the same currency.
     *
     * @throws IllegalArgumentException if currencies differ
     */
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot add different currencies: %s vs %s".formatted(this.currency, other.currency));
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Multiplies this amount by the given quantity.
     */
    public Money multiply(int quantity) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)), this.currency);
    }
}
