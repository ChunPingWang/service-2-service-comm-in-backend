package com.poc.order.domain.model;

/**
 * Enum representing the lifecycle states of an Order.
 * Valid transitions: CREATED -> PAYMENT_PENDING -> PAID -> SHIPPED
 */
public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    SHIPPED
}
