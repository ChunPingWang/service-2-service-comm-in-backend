package com.poc.notification.domain.model;

import java.time.Instant;

/**
 * Aggregate root representing a notification.
 * Immutable record -- state transitions return new instances.
 */
public record Notification(
        NotificationId notificationId,
        OrderId orderId,
        NotificationType type,
        NotificationStatus status,
        String message,
        Instant createdAt
) {

    public Notification {
        if (notificationId == null) {
            throw new IllegalArgumentException("notificationId must not be null");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }

    /**
     * Factory method to create a new Notification in {@link NotificationStatus#PENDING} status.
     *
     * @param notificationId unique notification identifier
     * @param orderId        the order this notification relates to
     * @param type           the type of notification
     * @param message        the notification message
     * @return a new Notification in PENDING status
     */
    public static Notification create(NotificationId notificationId, OrderId orderId,
                                       NotificationType type, String message) {
        if (notificationId == null) {
            throw new IllegalArgumentException("notificationId must not be null");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return new Notification(notificationId, orderId, type, NotificationStatus.PENDING,
                message, Instant.now());
    }

    /**
     * Transitions this notification from PENDING to SENT.
     *
     * @return a new Notification with SENT status
     * @throws IllegalStateException if the notification is not in PENDING status
     */
    public Notification markSent() {
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot mark sent: notification is in %s status, expected PENDING".formatted(status));
        }
        return new Notification(notificationId, orderId, type, NotificationStatus.SENT,
                message, createdAt);
    }

    /**
     * Transitions this notification from PENDING to FAILED.
     *
     * @return a new Notification with FAILED status
     * @throws IllegalStateException if the notification is not in PENDING status
     */
    public Notification markFailed() {
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot mark failed: notification is in %s status, expected PENDING".formatted(status));
        }
        return new Notification(notificationId, orderId, type, NotificationStatus.FAILED,
                message, createdAt);
    }
}
