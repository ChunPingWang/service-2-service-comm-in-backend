package com.poc.notification.unit.application;

import com.poc.notification.application.PaymentCompletedPayload;
import com.poc.notification.application.port.out.NotificationRepository;
import com.poc.notification.application.service.NotificationApplicationService;
import com.poc.notification.domain.model.Notification;
import com.poc.notification.domain.model.NotificationId;
import com.poc.notification.domain.model.NotificationStatus;
import com.poc.notification.domain.model.NotificationType;
import com.poc.notification.domain.model.OrderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationApplicationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationApplicationService(notificationRepository);
    }

    // ── handlePaymentCompleted ────────────────────────────────────────────────────

    @Nested
    @DisplayName("handlePaymentCompleted")
    class HandlePaymentCompleted {

        @Test
        @DisplayName("creates PAYMENT_CONFIRMED notification, saves it, marks sent, saves again")
        void handlePaymentCompleted_success() {
            // Arrange: mock repository to return whatever is passed in
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            PaymentCompletedPayload payload = new PaymentCompletedPayload(
                    "pay-001", "ord-001", "100.00", "USD");

            // Act
            service.handlePaymentCompleted(payload);

            // Assert: repository was called twice (once PENDING, once SENT)
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, times(2)).save(captor.capture());

            Notification firstSave = captor.getAllValues().get(0);
            Notification secondSave = captor.getAllValues().get(1);

            // First save should be PENDING with PAYMENT_CONFIRMED type
            assertEquals(NotificationStatus.PENDING, firstSave.status());
            assertEquals(NotificationType.PAYMENT_CONFIRMED, firstSave.type());
            assertEquals(new OrderId("ord-001"), firstSave.orderId());
            assertNotNull(firstSave.notificationId());
            assertTrue(firstSave.message().contains("pay-001"));
            assertTrue(firstSave.message().contains("ord-001"));

            // Second save should be SENT
            assertEquals(NotificationStatus.SENT, secondSave.status());
            assertEquals(NotificationType.PAYMENT_CONFIRMED, secondSave.type());
        }

        @Test
        @DisplayName("saves notification with correct fields")
        void handlePaymentCompleted_savesNotification() {
            // Arrange
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            PaymentCompletedPayload payload = new PaymentCompletedPayload(
                    "pay-002", "ord-002", "250.50", "EUR");

            // Act
            service.handlePaymentCompleted(payload);

            // Assert: verify repository.save called with correct fields
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, times(2)).save(captor.capture());

            Notification firstSave = captor.getAllValues().get(0);
            assertEquals(new OrderId("ord-002"), firstSave.orderId());
            assertEquals(NotificationType.PAYMENT_CONFIRMED, firstSave.type());
            assertEquals("Payment pay-002 completed for order ord-002", firstSave.message());
            assertNotNull(firstSave.createdAt());
        }
    }

    // ── findById ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns notification when found in repository")
        void findById_exists() {
            // Arrange
            NotificationId notificationId = new NotificationId("notif-123");
            Notification notification = Notification.create(
                    notificationId,
                    new OrderId("ord-001"),
                    NotificationType.PAYMENT_CONFIRMED,
                    "Payment completed for order ord-001"
            );
            when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

            // Act
            Optional<Notification> result = service.findById(notificationId);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(notificationId, result.get().notificationId());
            assertEquals(NotificationStatus.PENDING, result.get().status());
            verify(notificationRepository).findById(notificationId);
        }

        @Test
        @DisplayName("returns empty Optional when notification not found")
        void findById_notFound() {
            // Arrange
            NotificationId notificationId = new NotificationId("notif-nonexistent");
            when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

            // Act
            Optional<Notification> result = service.findById(notificationId);

            // Assert
            assertTrue(result.isEmpty());
            verify(notificationRepository).findById(notificationId);
        }
    }
}
