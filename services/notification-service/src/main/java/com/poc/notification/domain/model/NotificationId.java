package com.poc.notification.domain.model;

/**
 * Value object representing a unique notification identifier.
 */
public record NotificationId(String id) {

    public NotificationId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
