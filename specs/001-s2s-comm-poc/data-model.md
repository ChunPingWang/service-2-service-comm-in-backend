# Data Model: Service-to-Service Communication PoC

**Phase**: 1 (Foundation)
**Date**: 2026-02-14
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)
**Tech Stack**: Java 23 (records for immutability) | DDD | Hexagonal Architecture

---

## 1. Overview

This document defines all domain model types across the 5 bounded contexts
(Order, Product, Payment, Notification, Shipping). Every domain type is
modeled as an immutable Java record. Aggregate roots enforce invariants
through factory methods and validation. Value objects provide structural
equality via record semantics.

### Design Principles

- **Immutability**: All domain types are Java records. State changes produce
  new instances rather than mutating existing ones.
- **Structural Equality**: Value objects use record-generated `equals()` and
  `hashCode()` based on component values, not identity.
- **Aggregate Root Boundary**: Only aggregate roots are referenced across
  bounded contexts, always by their ID value object (never by direct object
  reference).
- **Domain Events**: Cross-context communication uses domain events. Events
  are immutable records containing only primitive/value-object data, never
  entity references.
- **Validation at Construction**: All records validate invariants in their
  compact constructors. Invalid states are unrepresentable.

---

## 2. Shared Value Objects

These value objects are used across multiple bounded contexts. Each bounded
context maintains its own copy (no shared library) to preserve context
autonomy. They are structurally identical but belong to separate packages.

### 2.1 Money

```java
package com.poc.<context>.domain.model;

import java.math.BigDecimal;

/**
 * Represents a monetary amount with currency.
 * Structural equality: two Money instances are equal if amount and currency match.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null) throw new IllegalArgumentException("Amount must not be null");
        if (amount.scale() < 0) throw new IllegalArgumentException("Amount scale must be non-negative");
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Amount must not be negative");
        if (currency == null || currency.isBlank())
            throw new IllegalArgumentException("Currency must not be blank");
        if (currency.length() != 3)
            throw new IllegalArgumentException("Currency must be ISO 4217 (3 characters)");
        currency = currency.toUpperCase();
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("Quantity must not be negative");
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)), this.currency);
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency))
            throw new IllegalArgumentException(
                "Currency mismatch: %s vs %s".formatted(this.currency, other.currency));
    }
}
```

### 2.2 ProductId

```java
public record ProductId(String id) {

    public ProductId {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("ProductId must not be blank");
    }
}
```

### 2.3 OrderId

```java
public record OrderId(String id) {

    public OrderId {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("OrderId must not be blank");
    }
}
```

---

## 3. Bounded Context: Order Service

**Package**: `com.poc.order.domain`

### 3.1 Aggregate Root -- Order

```java
package com.poc.order.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Order aggregate root. Manages the lifecycle of a customer order
 * from creation through payment to shipment.
 *
 * Invariants:
 *   - orderId must not be null
 *   - customerId must not be null
 *   - items must contain at least one OrderItem
 *   - status transitions must follow the defined state machine
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
        if (orderId == null) throw new IllegalArgumentException("OrderId must not be null");
        if (customerId == null) throw new IllegalArgumentException("CustomerId must not be null");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("Order must have at least one item");
        items = List.copyOf(items); // defensive immutable copy
        if (status == null) throw new IllegalArgumentException("OrderStatus must not be null");
        if (createdAt == null) throw new IllegalArgumentException("CreatedAt must not be null");
        if (updatedAt == null) throw new IllegalArgumentException("UpdatedAt must not be null");
    }

    /** Factory: create a new order in CREATED status. */
    public static Order create(OrderId orderId, CustomerId customerId, List<OrderItem> items) {
        var now = Instant.now();
        return new Order(orderId, customerId, items, OrderStatus.CREATED, now, now);
    }

    /** Transition to PAYMENT_PENDING. Only valid from CREATED. */
    public Order markPaymentPending() {
        requireStatus(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING);
        return new Order(orderId, customerId, items, OrderStatus.PAYMENT_PENDING,
                         createdAt, Instant.now());
    }

    /** Transition to PAID. Only valid from PAYMENT_PENDING. */
    public Order markPaid() {
        requireStatus(OrderStatus.PAYMENT_PENDING, OrderStatus.PAID);
        return new Order(orderId, customerId, items, OrderStatus.PAID,
                         createdAt, Instant.now());
    }

    /** Transition to SHIPPED. Only valid from PAID. */
    public Order markShipped() {
        requireStatus(OrderStatus.PAID, OrderStatus.SHIPPED);
        return new Order(orderId, customerId, items, OrderStatus.SHIPPED,
                         createdAt, Instant.now());
    }

    /** Calculate total amount across all items. */
    public Money totalAmount() {
        return items.stream()
            .map(OrderItem::lineTotal)
            .reduce(Money::add)
            .orElseThrow(() -> new IllegalStateException("Order has no items"));
    }

    private void requireStatus(OrderStatus expected, OrderStatus target) {
        if (this.status != expected)
            throw new IllegalStateException(
                "Cannot transition from %s to %s".formatted(this.status, target));
    }
}
```

### 3.2 Value Objects

```java
package com.poc.order.domain.model;

public record OrderId(String id) {
    public OrderId {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("OrderId must not be blank");
    }
}

public record CustomerId(String id) {
    public CustomerId {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("CustomerId must not be blank");
    }
}

/**
 * Represents a single line item in an order.
 *
 * Invariants:
 *   - quantity must be positive (>= 1)
 *   - unitPrice must not be null
 */
public record OrderItem(ProductId productId, int quantity, Money unitPrice) {

    public OrderItem {
        if (productId == null) throw new IllegalArgumentException("ProductId must not be null");
        if (quantity < 1) throw new IllegalArgumentException("Quantity must be at least 1");
        if (unitPrice == null) throw new IllegalArgumentException("UnitPrice must not be null");
    }

    /** Calculate line total: unitPrice * quantity. */
    public Money lineTotal() {
        return unitPrice.multiply(quantity);
    }
}
```

### 3.3 Enum -- OrderStatus

```java
package com.poc.order.domain.model;

/**
 * Order lifecycle states.
 *
 * Valid transitions:
 *   CREATED -> PAYMENT_PENDING -> PAID -> SHIPPED
 *
 * No backward transitions are permitted.
 */
public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    SHIPPED
}
```

### 3.4 Domain Event -- OrderCreatedEvent

```java
package com.poc.order.domain.event;

import java.time.Instant;

/**
 * Published when a new order is successfully created.
 * Consumed by: Payment Service (to initiate payment processing).
 *
 * All fields are primitives or value-object-equivalent strings
 * to avoid coupling between bounded contexts.
 */
public record OrderCreatedEvent(
    String orderId,
    String customerId,
    String productId,
    int quantity,
    String totalAmount,   // serialized Money (e.g., "99.99 USD")
    String currency,
    Instant timestamp
) {

    public OrderCreatedEvent {
        if (orderId == null || orderId.isBlank())
            throw new IllegalArgumentException("orderId must not be blank");
        if (customerId == null || customerId.isBlank())
            throw new IllegalArgumentException("customerId must not be blank");
        if (productId == null || productId.isBlank())
            throw new IllegalArgumentException("productId must not be blank");
        if (quantity < 1)
            throw new IllegalArgumentException("quantity must be at least 1");
        if (totalAmount == null || totalAmount.isBlank())
            throw new IllegalArgumentException("totalAmount must not be blank");
        if (currency == null || currency.isBlank())
            throw new IllegalArgumentException("currency must not be blank");
        if (timestamp == null)
            throw new IllegalArgumentException("timestamp must not be null");
    }
}
```

### 3.5 Order Service Validation Rules

| Rule | Field | Constraint |
|------|-------|------------|
| ORD-V01 | `orderId` | Must not be null or blank |
| ORD-V02 | `customerId` | Must not be null or blank |
| ORD-V03 | `items` | Must contain at least 1 item |
| ORD-V04 | `items` | List is defensively copied (immutable) |
| ORD-V05 | `status` | Must follow state machine: CREATED -> PAYMENT_PENDING -> PAID -> SHIPPED |
| ORD-V06 | `createdAt` | Must not be null |
| ORD-V07 | `updatedAt` | Must not be null; updated on every state transition |
| ORD-V08 | `OrderItem.quantity` | Must be >= 1 |
| ORD-V09 | `OrderItem.unitPrice` | Must not be null |
| ORD-V10 | `OrderItem.productId` | Must not be null |

---

## 4. Bounded Context: Product Service

**Package**: `com.poc.product.domain`

### 4.1 Aggregate Root -- Product

```java
package com.poc.product.domain.model;

/**
 * Product aggregate root. Represents an item available for purchase.
 *
 * Invariants:
 *   - productId must not be null
 *   - name must not be blank
 *   - price must not be null and must be positive
 *   - stockQuantity must be non-negative
 *   - category must not be null
 */
public record Product(
    ProductId productId,
    String name,
    String description,
    Money price,
    int stockQuantity,
    Category category
) {

    public Product {
        if (productId == null) throw new IllegalArgumentException("ProductId must not be null");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Product name must not be blank");
        if (description == null) description = "";
        if (price == null) throw new IllegalArgumentException("Price must not be null");
        if (price.amount().compareTo(java.math.BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Price must be positive");
        if (stockQuantity < 0)
            throw new IllegalArgumentException("Stock quantity must not be negative");
        if (category == null) throw new IllegalArgumentException("Category must not be null");
    }

    /** Check whether the requested quantity is available in stock. */
    public boolean hasAvailableStock(int requestedQuantity) {
        if (requestedQuantity < 1)
            throw new IllegalArgumentException("Requested quantity must be at least 1");
        return this.stockQuantity >= requestedQuantity;
    }

    /** Return a new Product with reduced stock after a reservation. */
    public Product reserveStock(int quantity) {
        if (!hasAvailableStock(quantity))
            throw new IllegalStateException(
                "Insufficient stock: requested %d, available %d"
                    .formatted(quantity, stockQuantity));
        return new Product(productId, name, description, price,
                           stockQuantity - quantity, category);
    }
}
```

### 4.2 Value Objects

```java
package com.poc.product.domain.model;

public record ProductId(String id) {
    public ProductId {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("ProductId must not be blank");
    }
}
```

### 4.3 Enum / Value Object -- Category

```java
package com.poc.product.domain.model;

/**
 * Product category. Modeled as an enum for the PoC.
 * In a production system, this could be a more flexible value object.
 */
public enum Category {
    ELECTRONICS,
    CLOTHING,
    BOOKS,
    HOME,
    SPORTS;

    /** Case-insensitive lookup. */
    public static Category fromString(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Category name must not be blank");
        try {
            return Category.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown category: " + name);
        }
    }
}
```

### 4.4 Product Service Validation Rules

| Rule | Field | Constraint |
|------|-------|------------|
| PRD-V01 | `productId` | Must not be null or blank |
| PRD-V02 | `name` | Must not be null or blank |
| PRD-V03 | `description` | Defaults to empty string if null |
| PRD-V04 | `price` | Must not be null; amount must be strictly positive |
| PRD-V05 | `stockQuantity` | Must be >= 0 |
| PRD-V06 | `category` | Must not be null; must be a valid Category value |
| PRD-V07 | `reserveStock(qty)` | Requested quantity must be <= available stock |

---

## 5. Bounded Context: Payment Service

**Package**: `com.poc.payment.domain`

### 5.1 Aggregate Root -- Payment

```java
package com.poc.payment.domain.model;

import java.time.Instant;

/**
 * Payment aggregate root. Represents a payment transaction for an order.
 *
 * Invariants:
 *   - paymentId must not be null
 *   - orderId must not be null
 *   - amount must not be null and must be positive
 *   - status transitions must follow the defined state machine
 *   - completedAt is null for PENDING, non-null for COMPLETED/FAILED
 */
public record Payment(
    PaymentId paymentId,
    OrderId orderId,
    Money amount,
    PaymentStatus status,
    Instant createdAt,
    Instant completedAt
) {

    public Payment {
        if (paymentId == null) throw new IllegalArgumentException("PaymentId must not be null");
        if (orderId == null) throw new IllegalArgumentException("OrderId must not be null");
        if (amount == null) throw new IllegalArgumentException("Amount must not be null");
        if (amount.amount().compareTo(java.math.BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Payment amount must be positive");
        if (status == null) throw new IllegalArgumentException("PaymentStatus must not be null");
        if (createdAt == null) throw new IllegalArgumentException("CreatedAt must not be null");
        if (status == PaymentStatus.PENDING && completedAt != null)
            throw new IllegalArgumentException("PENDING payment must not have completedAt");
        if ((status == PaymentStatus.COMPLETED || status == PaymentStatus.FAILED)
                && completedAt == null)
            throw new IllegalArgumentException(
                "COMPLETED/FAILED payment must have completedAt");
    }

    /** Factory: create a new payment in PENDING status. */
    public static Payment create(PaymentId paymentId, OrderId orderId, Money amount) {
        return new Payment(paymentId, orderId, amount, PaymentStatus.PENDING,
                           Instant.now(), null);
    }

    /** Transition to COMPLETED. Only valid from PENDING. */
    public Payment complete() {
        requireStatus(PaymentStatus.PENDING, PaymentStatus.COMPLETED);
        return new Payment(paymentId, orderId, amount, PaymentStatus.COMPLETED,
                           createdAt, Instant.now());
    }

    /** Transition to FAILED. Only valid from PENDING. */
    public Payment fail() {
        requireStatus(PaymentStatus.PENDING, PaymentStatus.FAILED);
        return new Payment(paymentId, orderId, amount, PaymentStatus.FAILED,
                           createdAt, Instant.now());
    }

    private void requireStatus(PaymentStatus expected, PaymentStatus target) {
        if (this.status != expected)
            throw new IllegalStateException(
                "Cannot transition from %s to %s".formatted(this.status, target));
    }
}
```

### 5.2 Value Objects

```java
package com.poc.payment.domain.model;

public record PaymentId(String id) {
    public PaymentId {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("PaymentId must not be blank");
    }
}

// OrderId and Money are context-local copies (see Section 2)
```

### 5.3 Enum -- PaymentStatus

```java
package com.poc.payment.domain.model;

/**
 * Payment lifecycle states.
 *
 * Valid transitions:
 *   PENDING -> COMPLETED
 *   PENDING -> FAILED
 *
 * Terminal states: COMPLETED, FAILED (no further transitions).
 */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED
}
```

### 5.4 Domain Event -- PaymentCompletedEvent

```java
package com.poc.payment.domain.event;

import java.time.Instant;

/**
 * Published when a payment is successfully completed.
 * Channel: Kafka topic "payment.completed"
 * Consumed by: Notification Service.
 */
public record PaymentCompletedEvent(
    String paymentId,
    String orderId,
    String amount,      // serialized decimal (e.g., "99.99")
    String currency,
    Instant timestamp
) {

    public PaymentCompletedEvent {
        if (paymentId == null || paymentId.isBlank())
            throw new IllegalArgumentException("paymentId must not be blank");
        if (orderId == null || orderId.isBlank())
            throw new IllegalArgumentException("orderId must not be blank");
        if (amount == null || amount.isBlank())
            throw new IllegalArgumentException("amount must not be blank");
        if (currency == null || currency.isBlank())
            throw new IllegalArgumentException("currency must not be blank");
        if (timestamp == null)
            throw new IllegalArgumentException("timestamp must not be null");
    }
}
```

### 5.5 Payment Service Validation Rules

| Rule | Field | Constraint |
|------|-------|------------|
| PAY-V01 | `paymentId` | Must not be null or blank |
| PAY-V02 | `orderId` | Must not be null or blank |
| PAY-V03 | `amount` | Must not be null; must be strictly positive |
| PAY-V04 | `status` | Must follow state machine: PENDING -> COMPLETED or PENDING -> FAILED |
| PAY-V05 | `createdAt` | Must not be null |
| PAY-V06 | `completedAt` | Must be null when PENDING; must be non-null when COMPLETED or FAILED |

---

## 6. Bounded Context: Notification Service

**Package**: `com.poc.notification.domain`

### 6.1 Aggregate Root -- Notification

```java
package com.poc.notification.domain.model;

import java.time.Instant;

/**
 * Notification aggregate root. Represents an event-driven notification
 * to be delivered (e.g., payment confirmation, shipping arrangement).
 *
 * Invariants:
 *   - notificationId must not be null
 *   - orderId must not be null
 *   - type must not be null
 *   - message must not be blank
 *   - status transitions must follow the defined state machine
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
        if (notificationId == null)
            throw new IllegalArgumentException("NotificationId must not be null");
        if (orderId == null)
            throw new IllegalArgumentException("OrderId must not be null");
        if (type == null)
            throw new IllegalArgumentException("NotificationType must not be null");
        if (status == null)
            throw new IllegalArgumentException("NotificationStatus must not be null");
        if (message == null || message.isBlank())
            throw new IllegalArgumentException("Message must not be blank");
        if (createdAt == null)
            throw new IllegalArgumentException("CreatedAt must not be null");
    }

    /** Factory: create a new notification in PENDING status. */
    public static Notification create(
            NotificationId notificationId,
            OrderId orderId,
            NotificationType type,
            String message) {
        return new Notification(notificationId, orderId, type,
                                NotificationStatus.PENDING, message, Instant.now());
    }

    /** Transition to SENT. Only valid from PENDING. */
    public Notification markSent() {
        requireStatus(NotificationStatus.PENDING, NotificationStatus.SENT);
        return new Notification(notificationId, orderId, type,
                                NotificationStatus.SENT, message, createdAt);
    }

    /** Transition to FAILED. Only valid from PENDING. */
    public Notification markFailed() {
        requireStatus(NotificationStatus.PENDING, NotificationStatus.FAILED);
        return new Notification(notificationId, orderId, type,
                                NotificationStatus.FAILED, message, createdAt);
    }

    private void requireStatus(NotificationStatus expected, NotificationStatus target) {
        if (this.status != expected)
            throw new IllegalStateException(
                "Cannot transition from %s to %s".formatted(this.status, target));
    }
}
```

### 6.2 Value Objects

```java
package com.poc.notification.domain.model;

public record NotificationId(String id) {
    public NotificationId {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("NotificationId must not be blank");
    }
}

// OrderId is a context-local copy (see Section 2)
```

### 6.3 Enums

```java
package com.poc.notification.domain.model;

/**
 * The type of notification being sent.
 */
public enum NotificationType {
    PAYMENT_CONFIRMED,
    SHIPPING_ARRANGED
}

/**
 * Notification delivery states.
 *
 * Valid transitions:
 *   PENDING -> SENT
 *   PENDING -> FAILED
 *
 * Terminal states: SENT, FAILED.
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED
}
```

### 6.4 Notification Service Validation Rules

| Rule | Field | Constraint |
|------|-------|------------|
| NTF-V01 | `notificationId` | Must not be null or blank |
| NTF-V02 | `orderId` | Must not be null or blank |
| NTF-V03 | `type` | Must not be null; must be a valid NotificationType |
| NTF-V04 | `status` | Must follow state machine: PENDING -> SENT or PENDING -> FAILED |
| NTF-V05 | `message` | Must not be null or blank |
| NTF-V06 | `createdAt` | Must not be null |

---

## 7. Bounded Context: Shipping Service

**Package**: `com.poc.shipping.domain`

### 7.1 Aggregate Root -- Shipment

```java
package com.poc.shipping.domain.model;

import java.time.Instant;

/**
 * Shipment aggregate root. Represents a shipping arrangement for a paid order.
 *
 * Invariants:
 *   - shipmentId must not be null
 *   - orderId must not be null
 *   - status transitions must follow the defined state machine
 *   - trackingNumber may be null for PENDING, must be non-null for IN_TRANSIT/DELIVERED
 */
public record Shipment(
    ShipmentId shipmentId,
    OrderId orderId,
    ShipmentStatus status,
    String trackingNumber,
    Instant createdAt
) {

    public Shipment {
        if (shipmentId == null) throw new IllegalArgumentException("ShipmentId must not be null");
        if (orderId == null) throw new IllegalArgumentException("OrderId must not be null");
        if (status == null) throw new IllegalArgumentException("ShipmentStatus must not be null");
        if (createdAt == null) throw new IllegalArgumentException("CreatedAt must not be null");
        if ((status == ShipmentStatus.IN_TRANSIT || status == ShipmentStatus.DELIVERED)
                && (trackingNumber == null || trackingNumber.isBlank()))
            throw new IllegalArgumentException(
                "TrackingNumber must not be blank for IN_TRANSIT/DELIVERED shipments");
    }

    /** Factory: create a new shipment in PENDING status. */
    public static Shipment create(ShipmentId shipmentId, OrderId orderId) {
        return new Shipment(shipmentId, orderId, ShipmentStatus.PENDING, null, Instant.now());
    }

    /** Transition to IN_TRANSIT. Only valid from PENDING. */
    public Shipment ship(String trackingNumber) {
        requireStatus(ShipmentStatus.PENDING, ShipmentStatus.IN_TRANSIT);
        if (trackingNumber == null || trackingNumber.isBlank())
            throw new IllegalArgumentException("TrackingNumber must not be blank");
        return new Shipment(shipmentId, orderId, ShipmentStatus.IN_TRANSIT,
                            trackingNumber, createdAt);
    }

    /** Transition to DELIVERED. Only valid from IN_TRANSIT. */
    public Shipment deliver() {
        requireStatus(ShipmentStatus.IN_TRANSIT, ShipmentStatus.DELIVERED);
        return new Shipment(shipmentId, orderId, ShipmentStatus.DELIVERED,
                            trackingNumber, createdAt);
    }

    private void requireStatus(ShipmentStatus expected, ShipmentStatus target) {
        if (this.status != expected)
            throw new IllegalStateException(
                "Cannot transition from %s to %s".formatted(this.status, target));
    }
}
```

### 7.2 Value Objects

```java
package com.poc.shipping.domain.model;

public record ShipmentId(String id) {
    public ShipmentId {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("ShipmentId must not be blank");
    }
}

// OrderId is a context-local copy (see Section 2)
```

### 7.3 Enum -- ShipmentStatus

```java
package com.poc.shipping.domain.model;

/**
 * Shipment lifecycle states.
 *
 * Valid transitions:
 *   PENDING -> IN_TRANSIT -> DELIVERED
 *
 * No backward transitions are permitted.
 */
public enum ShipmentStatus {
    PENDING,
    IN_TRANSIT,
    DELIVERED
}
```

### 7.4 Domain Event -- ShipmentArrangedEvent

```java
package com.poc.shipping.domain.event;

import java.time.Instant;

/**
 * Published when a shipment transitions to IN_TRANSIT.
 * Channel: Kafka topic "shipment.arranged"
 * Consumed by: Order Service (to transition Order to SHIPPED status).
 */
public record ShipmentArrangedEvent(
    String shipmentId,
    String orderId,
    String trackingNumber,
    Instant timestamp
) {

    public ShipmentArrangedEvent {
        if (shipmentId == null || shipmentId.isBlank())
            throw new IllegalArgumentException("shipmentId must not be blank");
        if (orderId == null || orderId.isBlank())
            throw new IllegalArgumentException("orderId must not be blank");
        if (trackingNumber == null || trackingNumber.isBlank())
            throw new IllegalArgumentException("trackingNumber must not be blank");
        if (timestamp == null)
            throw new IllegalArgumentException("timestamp must not be null");
    }
}
```

### 7.5 Shipping Service Validation Rules

| Rule | Field | Constraint |
|------|-------|------------|
| SHP-V01 | `shipmentId` | Must not be null or blank |
| SHP-V02 | `orderId` | Must not be null or blank |
| SHP-V03 | `status` | Must follow state machine: PENDING -> IN_TRANSIT -> DELIVERED |
| SHP-V04 | `trackingNumber` | May be null for PENDING; must be non-null and non-blank for IN_TRANSIT and DELIVERED |
| SHP-V05 | `createdAt` | Must not be null |

---

## 8. Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        BOUNDED CONTEXT MAP                              │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ ORDER SERVICE                                                    │   │
│  │                                                                  │   │
│  │  ┌────────────────┐        ┌──────────────┐                     │   │
│  │  │    Order        │───────▶│  OrderItem   │                     │   │
│  │  │ (Aggregate Root)│  1..*  │(Value Object)│                     │   │
│  │  └───────┬────────┘        └──────┬───────┘                     │   │
│  │          │ has                     │ has                          │   │
│  │          ▼                        ▼                              │   │
│  │  ┌──────────────┐  ┌────────────┐  ┌──────────┐  ┌───────┐    │   │
│  │  │   OrderId    │  │ CustomerId │  │ProductId │  │ Money │    │   │
│  │  │(Value Object)│  │(Val. Obj.) │  │(Val.Obj.)│  │(V.O.) │    │   │
│  │  └──────────────┘  └────────────┘  └──────────┘  └───────┘    │   │
│  │          │                                                      │   │
│  │          │ publishes                                             │   │
│  │          ▼                                                      │   │
│  │  ┌───────────────────┐                                          │   │
│  │  │ OrderCreatedEvent │                                          │   │
│  │  │  (Domain Event)   │                                          │   │
│  │  └───────────────────┘                                          │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ PRODUCT SERVICE                                                  │   │
│  │                                                                  │   │
│  │  ┌────────────────┐                                             │   │
│  │  │    Product      │                                             │   │
│  │  │(Aggregate Root) │                                             │   │
│  │  └───────┬────────┘                                             │   │
│  │          │ has                                                    │   │
│  │          ▼                                                      │   │
│  │  ┌──────────────┐  ┌──────────┐  ┌───────┐                    │   │
│  │  │  ProductId   │  │ Category │  │ Money │                    │   │
│  │  │(Value Object)│  │  (Enum)  │  │(V.O.) │                    │   │
│  │  └──────────────┘  └──────────┘  └───────┘                    │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ PAYMENT SERVICE                                                  │   │
│  │                                                                  │   │
│  │  ┌────────────────┐                                             │   │
│  │  │    Payment      │                                             │   │
│  │  │(Aggregate Root) │                                             │   │
│  │  └───────┬────────┘                                             │   │
│  │          │ has                          publishes                 │   │
│  │          ▼                                 │                     │   │
│  │  ┌──────────────┐  ┌─────────┐  ┌───────┐ ▼                   │   │
│  │  │  PaymentId   │  │ OrderId │  │ Money │ ┌─────────────────┐ │   │
│  │  │(Value Object)│  │ (V.O.)  │  │(V.O.) │ │PaymentCompleted │ │   │
│  │  └──────────────┘  └─────────┘  └───────┘ │   Event         │ │   │
│  │                                            └─────────────────┘ │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ NOTIFICATION SERVICE                                             │   │
│  │                                                                  │   │
│  │  ┌────────────────┐                                             │   │
│  │  │  Notification   │                                             │   │
│  │  │(Aggregate Root) │                                             │   │
│  │  └───────┬────────┘                                             │   │
│  │          │ has                                                    │   │
│  │          ▼                                                      │   │
│  │  ┌────────────────┐  ┌─────────┐  ┌──────────────────┐        │   │
│  │  │NotificationId  │  │ OrderId │  │NotificationType  │        │   │
│  │  │(Value Object)  │  │ (V.O.)  │  │    (Enum)        │        │   │
│  │  └────────────────┘  └─────────┘  └──────────────────┘        │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ SHIPPING SERVICE                                                 │   │
│  │                                                                  │   │
│  │  ┌────────────────┐                                             │   │
│  │  │    Shipment     │                                             │   │
│  │  │(Aggregate Root) │                                             │   │
│  │  └───────┬────────┘                                             │   │
│  │          │ has                                                    │   │
│  │          ▼                                                      │   │
│  │  ┌──────────────┐  ┌─────────┐                                 │   │
│  │  │  ShipmentId  │  │ OrderId │                                 │   │
│  │  │(Value Object)│  │ (V.O.)  │                                 │   │
│  │  └──────────────┘  └─────────┘                                 │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  CROSS-CONTEXT REFERENCES (by ID only):                                 │
│    Order ──(OrderId)──▶ Payment                                         │
│    Order ──(OrderId)──▶ Notification                                    │
│    Order ──(OrderId)──▶ Shipment                                        │
│    Order ──(ProductId)──▶ Product (via OrderItem)                       │
│    Order ──(CustomerId)──▶ [External: Customer]                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 9. State Transition Diagrams

### 9.1 Order Status State Machine

```
                ┌──────────┐
                │          │
                │ CREATED  │
                │          │
                └────┬─────┘
                     │
                     │ markPaymentPending()
                     │ [inventory confirmed via gRPC]
                     ▼
           ┌─────────────────┐
           │                 │
           │ PAYMENT_PENDING │
           │                 │
           └────────┬────────┘
                    │
                    │ markPaid()
                    │ [PaymentCompletedEvent received]
                    ▼
                ┌──────────┐
                │          │
                │   PAID   │
                │          │
                └────┬─────┘
                     │
                     │ markShipped()
                     │ [Shipment confirmed]
                     ▼
                ┌──────────┐
                │          │
                │ SHIPPED  │  (terminal state)
                │          │
                └──────────┘

    Allowed transitions:
      CREATED ────────────▶ PAYMENT_PENDING
      PAYMENT_PENDING ────▶ PAID
      PAID ────────────────▶ SHIPPED

    Disallowed: Any backward or skip transition throws IllegalStateException.
```

### 9.2 Payment Status State Machine

```
                ┌──────────┐
                │          │
                │ PENDING  │
                │          │
                └──┬───┬───┘
                   │   │
        complete() │   │ fail()
                   │   │
              ┌────▼┐ ┌▼────────┐
              │     │ │         │
              │COMP-│ │ FAILED  │  (terminal state)
              │LETED│ │         │
              │     │ └─────────┘
              └─────┘
          (terminal state)

    Allowed transitions:
      PENDING ──▶ COMPLETED
      PENDING ──▶ FAILED

    Both COMPLETED and FAILED are terminal (no further transitions).
    completedAt is set on transition out of PENDING.
```

### 9.3 Shipment Status State Machine

```
                ┌──────────┐
                │          │
                │ PENDING  │
                │          │
                └────┬─────┘
                     │
                     │ ship(trackingNumber)
                     │ [tracking number assigned]
                     ▼
              ┌─────────────┐
              │             │
              │ IN_TRANSIT  │
              │             │
              └──────┬──────┘
                     │
                     │ deliver()
                     ▼
              ┌─────────────┐
              │             │
              │  DELIVERED  │  (terminal state)
              │             │
              └─────────────┘

    Allowed transitions:
      PENDING ────▶ IN_TRANSIT  (requires trackingNumber)
      IN_TRANSIT ──▶ DELIVERED

    Disallowed: Any backward or skip transition throws IllegalStateException.
```

### 9.4 Notification Status State Machine

```
                ┌──────────┐
                │          │
                │ PENDING  │
                │          │
                └──┬───┬───┘
                   │   │
       markSent()  │   │ markFailed()
                   │   │
              ┌────▼┐ ┌▼────────┐
              │     │ │         │
              │SENT │ │ FAILED  │  (terminal state)
              │     │ │         │
              └─────┘ └─────────┘
          (terminal state)

    Allowed transitions:
      PENDING ──▶ SENT
      PENDING ──▶ FAILED
```

---

## 10. Cross-Context Communication Map

### 10.1 Event Flow Diagram

```
 ┌──────────────────────────────────────────────────────────────────────┐
 │                      EVENT FLOW TOPOLOGY                             │
 │                                                                      │
 │  ┌──────────┐   OrderCreatedEvent    ┌──────────────┐               │
 │  │  ORDER   │ ─────────────────────▶ │   PAYMENT    │               │
 │  │ SERVICE  │     (Kafka topic:      │   SERVICE    │               │
 │  │          │  "order.created")      │              │               │
 │  └──────────┘                        └──────┬───────┘               │
 │       ▲                                     │                        │
 │       │                                     │ PaymentCompletedEvent  │
 │       │ [REST: query status]                │ (Kafka topic:          │
 │       │                                     │  "payment.completed")  │
 │       │                                     ▼                        │
 │  ┌──────────┐                        ┌──────────────┐               │
 │  │ PRODUCT  │                        │ NOTIFICATION │               │
 │  │ SERVICE  │                        │   SERVICE    │               │
 │  │          │                        └──────┬───────┘               │
 │  └──────────┘                               │                        │
 │       ▲                                     │ ShippingRequestMessage  │
 │       │ [gRPC: check inventory,             │ (RabbitMQ exchange:     │
 │       │  get product details]               │  "shipping.exchange",   │
 │       │                                     │  routing key:           │
 │       │                                     │  "shipping.create")     │
 │  ┌────┴─────┐                               ▼                        │
 │  │  ORDER   │                        ┌──────────────┐               │
 │  │ SERVICE  │                        │   SHIPPING   │               │
 │  │ (caller) │                        │   SERVICE    │               │
 │  └──────────┘                        └──────────────┘               │
 └──────────────────────────────────────────────────────────────────────┘
```

### 10.2 Communication Matrix

| Source | Target | Protocol | Channel / Endpoint | Payload | Direction |
|--------|--------|----------|-------------------|---------|-----------|
| Order Service | Product Service | gRPC | `ProductService.GetProduct` | `GetProductRequest` / `ProductResponse` | Synchronous request-response |
| Order Service | Product Service | gRPC | `ProductService.CheckInventory` | `CheckInventoryRequest` / `InventoryResponse` | Synchronous request-response |
| Order Service | Payment Service | REST | `POST /api/v1/payments` | `PaymentRequest` / `PaymentResponse` | Synchronous request-response (with Circuit Breaker) |
| Order Service | Kafka | Kafka | Topic: `order.created` | `OrderCreatedEvent` | Asynchronous publish |
| Payment Service | Kafka | Kafka | Topic: `payment.completed` | `PaymentCompletedEvent` | Asynchronous publish |
| Notification Service | Kafka | Kafka | Topic: `payment.completed` | `PaymentCompletedEvent` | Asynchronous consume (consumer group: `notification-service`) |
| Notification Service | RabbitMQ | RabbitMQ | Exchange: `shipping.exchange`, Key: `shipping.create` | `ShippingRequestMessage` | Asynchronous publish |
| Shipping Service | RabbitMQ | RabbitMQ | Queue: `shipping.queue` | `ShippingRequestMessage` | Asynchronous consume |
| Shipping Service | Kafka | Kafka | Topic: `shipment.arranged` | `ShipmentArrangedEvent` | Asynchronous publish |
| Order Service | Kafka | Kafka | Topic: `shipment.arranged` | `ShipmentArrangedEvent` | Asynchronous consume (consumer group: `order-service`) |
| Client | Product Service | GraphQL | `/graphql` | GraphQL Query | Synchronous request-response (via API Gateway) |

### 10.3 Event Schemas Summary

| Event | Producer | Consumer(s) | Key | Topic / Queue | Idempotency |
|-------|----------|-------------|-----|---------------|-------------|
| `OrderCreatedEvent` | Order Service | Payment Service | `orderId` | Kafka: `order.created` | Deduplicate by `orderId` |
| `PaymentCompletedEvent` | Payment Service | Notification Service | `orderId` | Kafka: `payment.completed` | Deduplicate by `paymentId` |
| `ShippingRequestMessage` | Notification Service | Shipping Service | -- | RabbitMQ: `shipping.queue` | Deduplicate by `orderId` |
| `ShipmentArrangedEvent` | Shipping Service | Order Service | `orderId` | Kafka: `shipment.arranged` | Deduplicate by `shipmentId` |

### 10.4 Dead Letter Queue (DLQ) Configuration

| Source Queue / Topic | DLQ Destination | Trigger |
|---------------------|-----------------|---------|
| Kafka: `order.created` | Kafka: `order.created.dlq` | Consumer throws after max retries |
| Kafka: `payment.completed` | Kafka: `payment.completed.dlq` | Consumer throws after max retries |
| RabbitMQ: `shipping.queue` | RabbitMQ: `shipping.dlq` | Message nack after max retries |
| Kafka: `shipment.arranged` | Kafka: `shipment.arranged.dlq` | Consumer throws after max retries |

---

## 11. End-to-End Business Flow

The complete order processing flow traverses all 5 bounded contexts:

```
Step 1: Customer queries products
  Client ──[GraphQL via API Gateway]──▶ Product Service
  Product Service ──[ProductResponse]──▶ Client

Step 2: Customer creates an order
  Client ──[REST via API Gateway]──▶ Order Service
  Order Service ──[gRPC]──▶ Product Service (check inventory)
  Product Service ──[InventoryResponse]──▶ Order Service
  Order creates in CREATED status
  Order publishes OrderCreatedEvent to Kafka

Step 3: Payment processing
  Order Service ──[REST + Circuit Breaker]──▶ Payment Service
  Order transitions to PAYMENT_PENDING
  Payment Service processes payment
  Payment transitions from PENDING to COMPLETED
  Payment Service publishes PaymentCompletedEvent to Kafka
  Order transitions to PAID

Step 4: Notification
  Notification Service ──[Kafka consumer]──▶ receives PaymentCompletedEvent
  Notification created with type PAYMENT_CONFIRMED
  Notification Service ──[RabbitMQ]──▶ publishes ShippingRequestMessage

Step 5: Shipping
  Shipping Service ──[RabbitMQ consumer]──▶ receives ShippingRequestMessage
  Shipment created in PENDING status
  Shipment transitions to IN_TRANSIT (tracking number assigned)
  Shipping Service publishes ShipmentArrangedEvent to Kafka

Step 6: Order status closure
  Order Service ──[Kafka consumer]──▶ receives ShipmentArrangedEvent
  Order transitions to SHIPPED (terminal state)
```

---

## 12. Type Catalog

### 12.1 All Types by Bounded Context

| Bounded Context | Type Name | Kind | Package |
|----------------|-----------|------|---------|
| Order | `Order` | Aggregate Root (record) | `com.poc.order.domain.model` |
| Order | `OrderId` | Value Object (record) | `com.poc.order.domain.model` |
| Order | `CustomerId` | Value Object (record) | `com.poc.order.domain.model` |
| Order | `OrderItem` | Value Object (record) | `com.poc.order.domain.model` |
| Order | `OrderStatus` | Enum | `com.poc.order.domain.model` |
| Order | `Money` | Value Object (record) | `com.poc.order.domain.model` |
| Order | `ProductId` | Value Object (record) | `com.poc.order.domain.model` |
| Order | `OrderCreatedEvent` | Domain Event (record) | `com.poc.order.domain.event` |
| Product | `Product` | Aggregate Root (record) | `com.poc.product.domain.model` |
| Product | `ProductId` | Value Object (record) | `com.poc.product.domain.model` |
| Product | `Category` | Enum | `com.poc.product.domain.model` |
| Product | `Money` | Value Object (record) | `com.poc.product.domain.model` |
| Payment | `Payment` | Aggregate Root (record) | `com.poc.payment.domain.model` |
| Payment | `PaymentId` | Value Object (record) | `com.poc.payment.domain.model` |
| Payment | `PaymentStatus` | Enum | `com.poc.payment.domain.model` |
| Payment | `OrderId` | Value Object (record) | `com.poc.payment.domain.model` |
| Payment | `Money` | Value Object (record) | `com.poc.payment.domain.model` |
| Payment | `PaymentCompletedEvent` | Domain Event (record) | `com.poc.payment.domain.event` |
| Notification | `Notification` | Aggregate Root (record) | `com.poc.notification.domain.model` |
| Notification | `NotificationId` | Value Object (record) | `com.poc.notification.domain.model` |
| Notification | `NotificationType` | Enum | `com.poc.notification.domain.model` |
| Notification | `NotificationStatus` | Enum | `com.poc.notification.domain.model` |
| Notification | `OrderId` | Value Object (record) | `com.poc.notification.domain.model` |
| Shipping | `Shipment` | Aggregate Root (record) | `com.poc.shipping.domain.model` |
| Shipping | `ShipmentId` | Value Object (record) | `com.poc.shipping.domain.model` |
| Shipping | `ShipmentStatus` | Enum | `com.poc.shipping.domain.model` |
| Shipping | `OrderId` | Value Object (record) | `com.poc.shipping.domain.model` |
| Shipping | `ShipmentArrangedEvent` | Domain Event (record) | `com.poc.shipping.domain.event` |

### 12.2 Cross-Context ID References

| Owning Context | ID Type | Referenced In |
|---------------|---------|---------------|
| Order | `OrderId` | Payment, Notification, Shipping |
| Product | `ProductId` | Order (via OrderItem) |
| Order | `CustomerId` | Order only (external origin) |
| Payment | `PaymentId` | Payment only (referenced in events) |
| Notification | `NotificationId` | Notification only |
| Shipping | `ShipmentId` | Shipping only |

---

## 13. Design Decisions

| Decision | Rationale |
|----------|-----------|
| Java records for all domain types | Immutability by default; structural equality via generated `equals()`/`hashCode()`; compact syntax reduces boilerplate |
| Context-local copies of shared value objects (Money, OrderId, ProductId) | Preserves bounded context autonomy; avoids shared kernel coupling; each context can evolve independently |
| Validation in compact constructors | Invalid instances cannot exist; fail-fast at construction time; no separate validation layer needed |
| Factory methods for aggregate creation | Encapsulate initial state setup; enforce construction invariants; single point of entry for new aggregates |
| State transition methods return new instances | Immutability preserved; old state is never lost; supports event sourcing in the future |
| Domain events use primitive strings instead of value objects | Avoids coupling between bounded contexts; events are self-contained serializable DTOs |
| Defensive `List.copyOf()` in Order record | Prevents external mutation of the items list after construction |
| Terminal states with no outbound transitions | Prevents invalid state machines; `IllegalStateException` on any disallowed transition |

---

*Document Owner: Application Architecture Team*
*Input: [spec.md](spec.md), [plan.md](plan.md), PRD.md, TECH.md*
