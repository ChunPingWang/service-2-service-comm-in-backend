package com.poc.payment.application.port.in;

import com.poc.payment.application.ProcessPaymentCommand;
import com.poc.payment.domain.model.Payment;

/**
 * Inbound port for processing a new payment.
 */
public interface ProcessPaymentUseCase {

    /**
     * Processes a payment for the given command, returning the completed payment.
     *
     * @param command validated payment processing parameters
     * @return the completed {@link Payment}
     */
    Payment processPayment(ProcessPaymentCommand command);
}
