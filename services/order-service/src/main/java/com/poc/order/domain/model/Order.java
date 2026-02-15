package com.poc.order.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Aggregate root representing an Order.
 * Immutable record -- state transitions return new instances.
 * Factory method {@link #create} enforces invariants.
 */
public record Order(
        OrderId orderId,
        CustomerId customerId,
        List<OrderItem> items,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public Order {
        // Defensive copy to ensure true immutability
        if (items != null) {
            items = List.copyOf(items);
        }
    }

    /**
     * Factory method to create a new Order in {@link OrderStatus#CREATED} status.
     *
     * @throws IllegalArgumentException if orderId, customerId, or items is null/empty
     */
    public static Order create(OrderId orderId, CustomerId customerId, List<OrderItem> items) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be null or empty");
        }
        Instant now = Instant.now();
        return new Order(orderId, customerId, items, OrderStatus.CREATED, now, now);
    }

    /**
     * Transitions from CREATED to PAYMENT_PENDING.
     *
     * @throws IllegalStateException if current status is not CREATED
     */
    public Order markPaymentPending() {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException(
                    "Cannot mark payment pending: order is in %s status, expected CREATED".formatted(status));
        }
        return new Order(orderId, customerId, items, OrderStatus.PAYMENT_PENDING, createdAt, Instant.now());
    }

    /**
     * Transitions from PAYMENT_PENDING to PAID.
     *
     * @throws IllegalStateException if current status is not PAYMENT_PENDING
     */
    public Order markPaid() {
        if (status != OrderStatus.PAYMENT_PENDING) {
            throw new IllegalStateException(
                    "Cannot mark paid: order is in %s status, expected PAYMENT_PENDING".formatted(status));
        }
        return new Order(orderId, customerId, items, OrderStatus.PAID, createdAt, Instant.now());
    }

    /**
     * Transitions from PAID to SHIPPED.
     *
     * @throws IllegalStateException if current status is not PAID
     */
    public Order markShipped() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException(
                    "Cannot mark shipped: order is in %s status, expected PAID".formatted(status));
        }
        return new Order(orderId, customerId, items, OrderStatus.SHIPPED, createdAt, Instant.now());
    }

    /**
     * Calculates the total amount of the order by summing all item line totals.
     */
    public Money totalAmount() {
        return items.stream()
                .map(OrderItem::lineTotal)
                .reduce(Money::add)
                .orElseThrow(() -> new IllegalStateException("Order has no items"));
    }
}
