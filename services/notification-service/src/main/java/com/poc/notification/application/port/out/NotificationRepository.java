package com.poc.notification.application.port.out;

import com.poc.notification.domain.model.Notification;
import com.poc.notification.domain.model.NotificationId;

import java.util.Optional;

/**
 * Outbound port for notification persistence.
 *
 * <p>Implemented by infrastructure adapters (e.g., in-memory store, database).</p>
 */
public interface NotificationRepository {

    /**
     * Persists the given notification, returning the saved instance.
     *
     * @param notification the notification to save
     * @return the saved notification
     */
    Notification save(Notification notification);

    /**
     * Finds a notification by its unique identifier.
     *
     * @param notificationId the notification identifier to search for
     * @return the notification if found, or empty
     */
    Optional<Notification> findById(NotificationId notificationId);
}
