package com.poc.notification.application.port.in;

import com.poc.notification.domain.model.Notification;
import com.poc.notification.domain.model.NotificationId;

import java.util.Optional;

/**
 * Inbound port for querying existing notifications.
 */
public interface QueryNotificationUseCase {

    /**
     * Finds a notification by its unique identifier.
     *
     * @param notificationId the notification identifier to search for
     * @return the notification if found, or empty
     */
    Optional<Notification> findById(NotificationId notificationId);
}
