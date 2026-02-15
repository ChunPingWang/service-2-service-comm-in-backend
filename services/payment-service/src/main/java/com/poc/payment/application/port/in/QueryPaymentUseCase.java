package com.poc.payment.application.port.in;

import com.poc.payment.domain.model.Payment;
import com.poc.payment.domain.model.PaymentId;

import java.util.Optional;

/**
 * Inbound port for querying existing payments.
 */
public interface QueryPaymentUseCase {

    /**
     * Finds a payment by its unique identifier.
     *
     * @param paymentId the payment identifier to search for
     * @return the payment if found, or empty
     */
    Optional<Payment> findById(PaymentId paymentId);
}
