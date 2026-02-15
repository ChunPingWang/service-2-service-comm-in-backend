package com.poc.payment.application.service;

import com.poc.payment.application.OrderCreatedPayload;
import com.poc.payment.application.ProcessPaymentCommand;
import com.poc.payment.application.port.in.HandleOrderCreatedUseCase;
import com.poc.payment.application.port.in.ProcessPaymentUseCase;
import com.poc.payment.application.port.in.QueryPaymentUseCase;
import com.poc.payment.application.port.out.PaymentEventPort;
import com.poc.payment.application.port.out.PaymentRepository;
import com.poc.payment.domain.event.PaymentCompletedEvent;
import com.poc.payment.domain.model.Money;
import com.poc.payment.domain.model.OrderId;
import com.poc.payment.domain.model.Payment;
import com.poc.payment.domain.model.PaymentId;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service that orchestrates payment processing.
 *
 * <p>Implements {@link ProcessPaymentUseCase}, {@link QueryPaymentUseCase}, and
 * {@link HandleOrderCreatedUseCase} inbound ports, delegating persistence to
 * the {@link PaymentRepository} outbound port and event publishing to
 * the {@link PaymentEventPort} outbound port.</p>
 */
@Service
public class PaymentApplicationService implements ProcessPaymentUseCase, QueryPaymentUseCase, HandleOrderCreatedUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentEventPort paymentEventPort;

    public PaymentApplicationService(PaymentRepository paymentRepository,
                                     PaymentEventPort paymentEventPort) {
        this.paymentRepository = paymentRepository;
        this.paymentEventPort = paymentEventPort;
    }

    /**
     * Processes a payment by:
     * <ol>
     *   <li>Generating a unique PaymentId</li>
     *   <li>Creating a PENDING payment via the domain factory</li>
     *   <li>Saving the initial PENDING payment</li>
     *   <li>Completing the payment (PoC: always succeeds)</li>
     *   <li>Saving the COMPLETED payment</li>
     * </ol>
     *
     * @param command the validated payment processing command
     * @return the completed payment
     */
    @Override
    public Payment processPayment(ProcessPaymentCommand command) {
        // 1. Generate a unique payment identifier
        PaymentId paymentId = new PaymentId(UUID.randomUUID().toString());

        // 2. Build domain value objects and create PENDING payment
        OrderId orderId = new OrderId(command.orderId());
        Money money = new Money(command.amount(), command.currency());
        Payment pendingPayment = Payment.create(paymentId, orderId, money);

        // 3. Save initial PENDING payment
        paymentRepository.save(pendingPayment);

        // 4. Complete the payment (for PoC, processing always succeeds)
        Payment completedPayment = pendingPayment.complete();

        // 5. Save completed payment
        paymentRepository.save(completedPayment);

        // 6. Return the completed payment
        return completedPayment;
    }

    /**
     * Handles an order-created event by processing payment and publishing
     * a payment-completed event.
     *
     * <ol>
     *   <li>Parses the totalAmount string to BigDecimal</li>
     *   <li>Creates a ProcessPaymentCommand</li>
     *   <li>Delegates to processPayment</li>
     *   <li>Publishes a PaymentCompletedEvent via the outbound event port</li>
     * </ol>
     *
     * @param payload the order-created event payload
     */
    @Override
    public void handleOrderCreated(OrderCreatedPayload payload) {
        // 1. Parse totalAmount from serialized decimal string
        BigDecimal amount = new BigDecimal(payload.totalAmount());

        // 2. Create command and process payment
        ProcessPaymentCommand command = new ProcessPaymentCommand(
                payload.orderId(), amount, payload.currency());
        Payment completedPayment = processPayment(command);

        // 3. Publish PaymentCompletedEvent
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                completedPayment.paymentId().id(),
                completedPayment.orderId().id(),
                completedPayment.amount().amount().toPlainString(),
                completedPayment.amount().currency(),
                Instant.now()
        );
        paymentEventPort.publish(event);
    }

    @Override
    public Optional<Payment> findById(PaymentId paymentId) {
        return paymentRepository.findById(paymentId);
    }
}
