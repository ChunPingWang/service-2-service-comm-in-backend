package com.poc.product.domain.model;

import java.math.BigDecimal;

/**
 * Value object representing a monetary amount with currency.
 * This is a context-local copy for the product service domain.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (currency == null) {
            throw new IllegalArgumentException("currency must not be null");
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be exactly 3 characters");
        }
    }

    /**
     * Adds another Money value with the same currency.
     *
     * @param other the money to add
     * @return a new Money representing the sum
     * @throws IllegalArgumentException if currencies differ
     */
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot add different currencies: %s and %s".formatted(this.currency, other.currency));
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Multiplies this money by an integer quantity.
     *
     * @param multiplier a strictly positive integer
     * @return a new Money representing the product
     * @throws IllegalArgumentException if multiplier is not positive
     */
    public Money multiply(int multiplier) {
        if (multiplier < 1) {
            throw new IllegalArgumentException("multiplier must be positive, got: " + multiplier);
        }
        return new Money(this.amount.multiply(BigDecimal.valueOf(multiplier)), this.currency);
    }
}
