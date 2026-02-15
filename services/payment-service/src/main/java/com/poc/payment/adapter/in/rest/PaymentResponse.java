package com.poc.payment.adapter.in.rest;

/**
 * REST response DTO representing a payment.
 *
 * @param id          the payment identifier
 * @param orderId     the order identifier
 * @param amount      the monetary amount details
 * @param status      the payment status
 * @param createdAt   ISO-8601 formatted creation timestamp
 * @param completedAt ISO-8601 formatted completion timestamp (null if not yet completed)
 */
public record PaymentResponse(
        String id,
        String orderId,
        MoneyResponse amount,
        String status,
        String createdAt,
        String completedAt
) {

    /**
     * Nested DTO representing a monetary amount in a REST response.
     *
     * @param amount   the numeric amount
     * @param currency the 3-character currency code
     */
    public record MoneyResponse(double amount, String currency) {
    }
}
