package com.poc.payment.application;

import java.math.BigDecimal;

/**
 * Self-validating command object for payment processing.
 *
 * <p>Co-located in the application package (rather than in {@code port.in})
 * because the ArchUnit architecture rules enforce that all classes in
 * {@code port.in} must be interfaces.</p>
 *
 * @param orderId  the order this payment is for (must not be blank)
 * @param amount   the monetary amount (must not be null)
 * @param currency the 3-character currency code (must not be blank)
 */
public record ProcessPaymentCommand(String orderId, BigDecimal amount, String currency) {
    public ProcessPaymentCommand {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId required");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency required");
        }
    }
}
