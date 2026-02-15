package com.poc.order.adapter.out.rest;

import com.poc.order.application.port.out.PaymentPort.PaymentResult;
import com.poc.order.domain.model.Money;
import com.poc.order.domain.model.OrderId;

/**
 * Maps between the domain models and REST DTOs used to communicate
 * with the Payment Service.
 * Stateless utility class -- all methods are static.
 */
public final class PaymentRestMapper {

    private PaymentRestMapper() {
        // prevent instantiation
    }

    /**
     * Builds the outbound REST request body for the Payment Service.
     */
    public static PaymentRestRequest toRequest(OrderId orderId, Money amount) {
        return new PaymentRestRequest(
                orderId.id(),
                new PaymentRestRequest.MoneyDto(amount.amount().doubleValue(), amount.currency())
        );
    }

    /**
     * Converts the Payment Service REST response to a domain {@link PaymentResult}.
     */
    public static PaymentResult fromResponse(PaymentRestResponse response) {
        return new PaymentResult(response.id(), response.status());
    }

    /**
     * Request body sent to the Payment Service.
     */
    public record PaymentRestRequest(String orderId, MoneyDto amount) {
        public record MoneyDto(double amount, String currency) {}
    }

    /**
     * Response body received from the Payment Service.
     */
    public record PaymentRestResponse(
            String id,
            String orderId,
            String status,
            Object amount,
            String createdAt,
            String completedAt
    ) {}
}
