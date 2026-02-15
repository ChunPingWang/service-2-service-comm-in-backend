package com.poc.payment.application.port.out;

import com.poc.payment.domain.model.Payment;
import com.poc.payment.domain.model.PaymentId;

import java.util.Optional;

/**
 * Outbound port for payment persistence.
 *
 * <p>Implemented by infrastructure adapters (e.g., in-memory store, database).</p>
 */
public interface PaymentRepository {

    /**
     * Persists the given payment, returning the saved instance.
     *
     * @param payment the payment to save
     * @return the saved payment
     */
    Payment save(Payment payment);

    /**
     * Finds a payment by its unique identifier.
     *
     * @param paymentId the payment identifier to search for
     * @return the payment if found, or empty
     */
    Optional<Payment> findById(PaymentId paymentId);
}
