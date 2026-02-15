package com.poc.payment.adapter.in.rest;

/**
 * REST request DTO for creating a payment.
 *
 * @param orderId the order identifier
 * @param amount  the monetary amount details
 */
public record PaymentRequest(String orderId, MoneyRequest amount) {

    /**
     * Nested DTO representing a monetary amount in a REST request.
     *
     * @param amount   the numeric amount
     * @param currency the 3-character currency code
     */
    public record MoneyRequest(double amount, String currency) {
    }
}
