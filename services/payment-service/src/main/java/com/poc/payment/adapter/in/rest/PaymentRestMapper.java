package com.poc.payment.adapter.in.rest;

import com.poc.payment.application.ProcessPaymentCommand;
import com.poc.payment.domain.model.Payment;

import java.math.BigDecimal;

/**
 * Mapper that converts between REST DTOs and application/domain objects.
 *
 * <p>This mapper ensures that data crossing the adapter boundary is properly
 * transformed, keeping the adapter layer decoupled from domain internals.</p>
 */
public final class PaymentRestMapper {

    private PaymentRestMapper() {
        // Utility class — no instantiation
    }

    /**
     * Converts a REST request DTO into an application command.
     *
     * @param request the inbound REST request
     * @return a validated {@link ProcessPaymentCommand}
     */
    public static ProcessPaymentCommand toCommand(PaymentRequest request) {
        return new ProcessPaymentCommand(
                request.orderId(),
                BigDecimal.valueOf(request.amount().amount()),
                request.amount().currency()
        );
    }

    /**
     * Converts a domain {@link Payment} into a REST response DTO.
     *
     * @param payment the domain payment
     * @return the outbound REST response
     */
    public static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.paymentId().id(),
                payment.orderId().id(),
                new PaymentResponse.MoneyResponse(
                        payment.amount().amount().doubleValue(),
                        payment.amount().currency()
                ),
                payment.status().name(),
                payment.createdAt().toString(),
                payment.completedAt() != null ? payment.completedAt().toString() : null
        );
    }
}
