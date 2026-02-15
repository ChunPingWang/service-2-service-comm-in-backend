package com.poc.payment.application;

/**
 * Self-validating payload object for order-created events.
 *
 * <p>Co-located in the application package (rather than in {@code port.in})
 * because the ArchUnit architecture rules enforce that all classes in
 * {@code port.in} must be interfaces.</p>
 *
 * @param orderId     the order identifier (must not be blank)
 * @param productId   the product identifier
 * @param quantity    the quantity ordered
 * @param totalAmount the serialized decimal amount (e.g. "99.99")
 * @param currency    the 3-character currency code (must not be blank)
 */
public record OrderCreatedPayload(
        String orderId,
        String productId,
        int quantity,
        String totalAmount,
        String currency
) {
    public OrderCreatedPayload {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId required");
        }
        if (totalAmount == null || totalAmount.isBlank()) {
            throw new IllegalArgumentException("totalAmount required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency required");
        }
    }
}
