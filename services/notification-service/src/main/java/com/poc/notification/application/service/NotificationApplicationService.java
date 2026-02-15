package com.poc.notification.application.service;

import com.poc.notification.application.PaymentCompletedPayload;
import com.poc.notification.application.port.in.HandlePaymentEventUseCase;
import com.poc.notification.application.port.in.QueryNotificationUseCase;
import com.poc.notification.application.port.out.NotificationRepository;
import com.poc.notification.domain.model.Notification;
import com.poc.notification.domain.model.NotificationId;
import com.poc.notification.domain.model.NotificationType;
import com.poc.notification.domain.model.OrderId;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Application service that orchestrates notification creation and delivery.
 *
 * <p>Implements {@link HandlePaymentEventUseCase} and {@link QueryNotificationUseCase}
 * inbound ports, delegating persistence to the {@link NotificationRepository} outbound port.</p>
 */
@Service
public class NotificationApplicationService implements HandlePaymentEventUseCase, QueryNotificationUseCase {

    private final NotificationRepository notificationRepository;

    public NotificationApplicationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Handles a payment-completed event by:
     * <ol>
     *   <li>Generating a unique NotificationId</li>
     *   <li>Creating a PENDING notification with PAYMENT_CONFIRMED type</li>
     *   <li>Saving the PENDING notification</li>
     *   <li>Marking the notification as SENT</li>
     *   <li>Saving the SENT notification</li>
     * </ol>
     *
     * @param payload the payment-completed event payload
     */
    @Override
    public void handlePaymentCompleted(PaymentCompletedPayload payload) {
        // 1. Generate a unique notification identifier
        NotificationId notificationId = new NotificationId(UUID.randomUUID().toString());

        // 2. Build domain value objects and create PENDING notification
        OrderId orderId = new OrderId(payload.orderId());
        String message = "Payment %s completed for order %s".formatted(
                payload.paymentId(), payload.orderId());

        Notification pendingNotification = Notification.create(
                notificationId, orderId, NotificationType.PAYMENT_CONFIRMED, message);

        // 3. Save initial PENDING notification
        notificationRepository.save(pendingNotification);

        // 4. Mark notification as SENT (for PoC, sending always succeeds)
        Notification sentNotification = pendingNotification.markSent();

        // 5. Save SENT notification
        notificationRepository.save(sentNotification);
    }

    @Override
    public Optional<Notification> findById(NotificationId notificationId) {
        return notificationRepository.findById(notificationId);
    }
}
