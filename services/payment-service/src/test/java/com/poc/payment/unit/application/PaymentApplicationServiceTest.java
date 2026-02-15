package com.poc.payment.unit.application;

import com.poc.payment.application.ProcessPaymentCommand;
import com.poc.payment.application.port.in.ProcessPaymentUseCase;
import com.poc.payment.application.port.out.PaymentEventPort;
import com.poc.payment.application.port.out.PaymentRepository;
import com.poc.payment.application.service.PaymentApplicationService;
import com.poc.payment.domain.model.Money;
import com.poc.payment.domain.model.OrderId;
import com.poc.payment.domain.model.Payment;
import com.poc.payment.domain.model.PaymentId;
import com.poc.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentApplicationServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventPort paymentEventPort;

    private PaymentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentApplicationService(paymentRepository, paymentEventPort);
    }

    // ── processPayment ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("processPayment")
    class ProcessPayment {

        @Test
        @DisplayName("creates payment in PENDING, completes it, and saves twice")
        void processPayment_success() {
            // Arrange: mock repository to return whatever is passed in
            when(paymentRepository.save(any(Payment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ProcessPaymentCommand command = new ProcessPaymentCommand(
                    "ord-001", new BigDecimal("100.00"), "USD");

            // Act
            Payment result = service.processPayment(command);

            // Assert: final payment is COMPLETED
            assertNotNull(result);
            assertEquals(PaymentStatus.COMPLETED, result.status());
            assertEquals(new OrderId("ord-001"), result.orderId());
            assertEquals(new Money(new BigDecimal("100.00"), "USD"), result.amount());
            assertNotNull(result.completedAt());

            // Verify repository was called twice (once for PENDING, once for COMPLETED)
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(2)).save(captor.capture());

            Payment firstSave = captor.getAllValues().get(0);
            Payment secondSave = captor.getAllValues().get(1);

            assertEquals(PaymentStatus.PENDING, firstSave.status());
            assertEquals(PaymentStatus.COMPLETED, secondSave.status());
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null orderId")
        void processPayment_nullOrderId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProcessPaymentCommand(null, new BigDecimal("10.00"), "USD"));
        }

        @Test
        @DisplayName("throws IllegalArgumentException for blank orderId")
        void processPayment_blankOrderId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProcessPaymentCommand("  ", new BigDecimal("10.00"), "USD"));
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null amount")
        void processPayment_nullAmount() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProcessPaymentCommand("ord-001", null, "USD"));
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null currency")
        void processPayment_nullCurrency() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProcessPaymentCommand("ord-001", new BigDecimal("10.00"), null));
        }

        @Test
        @DisplayName("throws IllegalArgumentException for blank currency")
        void processPayment_blankCurrency() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProcessPaymentCommand("ord-001", new BigDecimal("10.00"), ""));
        }

        @Test
        @DisplayName("final saved payment has COMPLETED status")
        void processPayment_savesCompletedPayment() {
            // Arrange
            when(paymentRepository.save(any(Payment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ProcessPaymentCommand command = new ProcessPaymentCommand(
                    "ord-002", new BigDecimal("250.50"), "EUR");

            // Act
            Payment result = service.processPayment(command);

            // Assert: the returned payment is COMPLETED
            assertEquals(PaymentStatus.COMPLETED, result.status());
            assertNotNull(result.completedAt());

            // Verify the last save call was with a COMPLETED payment
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(2)).save(captor.capture());

            Payment lastSaved = captor.getAllValues().get(1);
            assertEquals(PaymentStatus.COMPLETED, lastSaved.status());
            assertEquals(new OrderId("ord-002"), lastSaved.orderId());
            assertEquals(new Money(new BigDecimal("250.50"), "EUR"), lastSaved.amount());
        }
    }

    // ── findById ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns payment when found in repository")
        void findById_exists() {
            // Arrange
            PaymentId paymentId = new PaymentId("pay-123");
            Payment payment = Payment.create(
                    paymentId,
                    new OrderId("ord-001"),
                    new Money(new BigDecimal("50.00"), "USD")
            );
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            // Act
            Optional<Payment> result = service.findById(paymentId);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(paymentId, result.get().paymentId());
            assertEquals(PaymentStatus.PENDING, result.get().status());
            verify(paymentRepository).findById(paymentId);
        }

        @Test
        @DisplayName("returns empty Optional when payment not found")
        void findById_notFound() {
            // Arrange
            PaymentId paymentId = new PaymentId("pay-nonexistent");
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

            // Act
            Optional<Payment> result = service.findById(paymentId);

            // Assert
            assertTrue(result.isEmpty());
            verify(paymentRepository).findById(paymentId);
        }
    }
}
