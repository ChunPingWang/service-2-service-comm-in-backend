package com.poc.payment.adapter.in.rest;

import com.poc.payment.application.ProcessPaymentCommand;
import com.poc.payment.application.port.in.ProcessPaymentUseCase;
import com.poc.payment.application.port.in.QueryPaymentUseCase;
import com.poc.payment.domain.model.Payment;
import com.poc.payment.domain.model.PaymentId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter that exposes payment operations as HTTP endpoints.
 *
 * <p>This controller depends only on inbound ports ({@link ProcessPaymentUseCase}
 * and {@link QueryPaymentUseCase}), following the hexagonal architecture rule
 * that adapters must not access application services directly.</p>
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final QueryPaymentUseCase queryPaymentUseCase;

    public PaymentController(ProcessPaymentUseCase processPaymentUseCase,
                             QueryPaymentUseCase queryPaymentUseCase) {
        this.processPaymentUseCase = processPaymentUseCase;
        this.queryPaymentUseCase = queryPaymentUseCase;
    }

    /**
     * Processes a new payment.
     *
     * @param request the payment request body
     * @return the processed payment response
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(@RequestBody PaymentRequest request) {
        ProcessPaymentCommand command = PaymentRestMapper.toCommand(request);
        Payment payment = processPaymentUseCase.processPayment(command);
        PaymentResponse response = PaymentRestMapper.toResponse(payment);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a payment by its identifier.
     *
     * @param paymentId the payment identifier
     * @return the payment response if found, or 404
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String paymentId) {
        return queryPaymentUseCase.findById(new PaymentId(paymentId))
                .map(PaymentRestMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
