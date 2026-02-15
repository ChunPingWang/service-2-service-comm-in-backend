package com.poc.notification.application;

/**
 * Self-validating payload object for payment-completed events.
 *
 * <p>Co-located in the application package (rather than in {@code port.in})
 * because the ArchUnit architecture rules enforce that all classes in
 * {@code port.in} must be interfaces.</p>
 *
 * @param paymentId the payment identifier (must not be blank)
 * @param orderId   the order identifier (must not be blank)
 * @param amount    the serialized decimal amount (e.g. "99.99")
 * @param currency  the 3-character currency code (must not be blank)
 */
public record PaymentCompletedPayload(
        String paymentId,
        String orderId,
        String amount,
        String currency
) {
    public PaymentCompletedPayload {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("paymentId required");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId required");
        }
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("amount required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency required");
        }
    }
}
