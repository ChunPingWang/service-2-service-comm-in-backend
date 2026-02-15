package com.poc.order.adapter.in.rest;

/**
 * Outbound REST response DTO representing an order.
 * Flattened for the PoC single-item order model.
 */
public record OrderResponse(
        String id,
        String customerId,
        String productId,
        int quantity,
        MoneyResponse totalAmount,
        String status,
        String createdAt,
        String updatedAt
) {

    /**
     * Nested DTO for monetary values in REST responses.
     */
    public record MoneyResponse(double amount, String currency) {}
}
