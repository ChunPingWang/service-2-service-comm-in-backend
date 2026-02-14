# Kafka Event Contracts

## Overview

Kafka is used for asynchronous event-driven communication between
bounded contexts. All events follow a common envelope structure
with domain-specific payloads.

## Topics

| Topic | Producer | Consumer(s) | Key | Partitions |
|-------|----------|-------------|-----|------------|
| `order.created` | Order Service | Payment Service | orderId | 3 |
| `payment.completed` | Payment Service | Notification Service | orderId | 3 |
| `shipment.arranged` | Shipping Service | Order Service | orderId | 3 |

> **Note**: Payment Service consumes `order.created` as the **async
> alternative** path. The primary E2E flow uses REST (Order→Payment)
> for synchronous payment. Both paths exist for pattern comparison.

## Event Envelope

All Kafka events share this envelope structure:

```json
{
  "eventId": "string (UUID)",
  "eventType": "string",
  "timestamp": "string (ISO-8601)",
  "source": "string (service name)",
  "correlationId": "string (trace ID)",
  "payload": { }
}
```

## Event: OrderCreatedEvent

**Topic**: `order.created`
**Key**: `orderId`
**Producer**: Order Service
**Consumer**: Payment Service

```json
{
  "eventId": "evt-550e8400-e29b-41d4-a716-446655440000",
  "eventType": "ORDER_CREATED",
  "timestamp": "2026-02-14T10:30:00Z",
  "source": "order-service",
  "correlationId": "trace-abc123",
  "payload": {
    "orderId": "ord-001",
    "customerId": "cust-001",
    "productId": "prod-001",
    "quantity": 2,
    "totalAmount": {
      "amount": 59.98,
      "currency": "USD"
    }
  }
}
```

### Consumer Requirements

- Consumer group: `payment-service`
- Acknowledgment: Manual (after successful processing)
- Error handling: Retry 3 times with exponential backoff, then
  route to DLQ topic `order.created.dlq`
- Idempotency: Consumer MUST be idempotent (deduplicate by eventId)

## Event: PaymentCompletedEvent

**Topic**: `payment.completed`
**Key**: `orderId`
**Producer**: Payment Service
**Consumer**: Notification Service

```json
{
  "eventId": "evt-660e8400-e29b-41d4-a716-446655440001",
  "eventType": "PAYMENT_COMPLETED",
  "timestamp": "2026-02-14T10:30:05Z",
  "source": "payment-service",
  "correlationId": "trace-abc123",
  "payload": {
    "paymentId": "pay-001",
    "orderId": "ord-001",
    "amount": {
      "amount": 59.98,
      "currency": "USD"
    },
    "status": "COMPLETED"
  }
}
```

### Consumer Requirements

- Consumer group: `notification-service`
- Acknowledgment: Manual (after successful processing)
- Error handling: Retry 3 times with exponential backoff, then
  route to DLQ topic `payment.completed.dlq`
- Idempotency: Consumer MUST be idempotent (deduplicate by eventId)

## Event: ShipmentArrangedEvent

**Topic**: `shipment.arranged`
**Key**: `orderId`
**Producer**: Shipping Service
**Consumer**: Order Service

```json
{
  "eventId": "evt-880e8400-e29b-41d4-a716-446655440003",
  "eventType": "SHIPMENT_ARRANGED",
  "timestamp": "2026-02-14T10:30:15Z",
  "source": "shipping-service",
  "correlationId": "trace-abc123",
  "payload": {
    "shipmentId": "shp-001",
    "orderId": "ord-001",
    "trackingNumber": "TRK-20260214-001",
    "status": "IN_TRANSIT"
  }
}
```

### Consumer Requirements

- Consumer group: `order-service`
- Acknowledgment: Manual (after successful processing)
- Error handling: Retry 3 times with exponential backoff, then
  route to DLQ topic `shipment.arranged.dlq`
- Idempotency: Consumer MUST be idempotent (deduplicate by eventId)

## Dead Letter Queue (DLQ)

| DLQ Topic | Source Topic | Purpose |
|-----------|-------------|---------|
| `order.created.dlq` | `order.created` | Failed order event processing |
| `payment.completed.dlq` | `payment.completed` | Failed payment event processing |
| `shipment.arranged.dlq` | `shipment.arranged` | Failed shipment event processing |

DLQ messages retain the original event envelope plus error metadata:

```json
{
  "originalEvent": { },
  "error": {
    "message": "string",
    "exceptionClass": "string",
    "retryCount": 3,
    "failedAt": "string (ISO-8601)"
  }
}
```

## Serialization

- Key: String (orderId)
- Value: JSON (Jackson serialization)
- Schema validation: JSON Schema at producer side
