# RabbitMQ Message Contracts

## Overview

RabbitMQ is used for point-to-point asynchronous messaging between
Notification Service and Shipping Service. It uses topic exchanges
with routing keys for message routing.

## Exchange & Queue Topology

```text
┌─────────────────────────┐
│  shipping.exchange       │  (Topic Exchange)
│  Type: topic            │
└──────────┬──────────────┘
           │ routing key: shipping.create
           ▼
┌─────────────────────────┐     ┌─────────────────────────┐
│  shipping.queue          │────▶│  Shipping Service        │
│  Durable: true          │     │  (Consumer)              │
│  DLQ: shipping.dlq      │     └─────────────────────────┘
└─────────────────────────┘

┌─────────────────────────┐
│  shipping.dlq            │  (Dead Letter Queue)
│  Durable: true          │
└─────────────────────────┘
```

## Message: ShippingRequest

**Exchange**: `shipping.exchange`
**Routing Key**: `shipping.create`
**Queue**: `shipping.queue`
**Producer**: Notification Service
**Consumer**: Shipping Service

```json
{
  "messageId": "msg-770e8400-e29b-41d4-a716-446655440002",
  "messageType": "SHIPPING_REQUEST",
  "timestamp": "2026-02-14T10:30:10Z",
  "source": "notification-service",
  "correlationId": "trace-abc123",
  "payload": {
    "orderId": "ord-001",
    "customerId": "cust-001",
    "productId": "prod-001",
    "quantity": 2,
    "shippingAddress": {
      "line1": "123 Main St",
      "city": "Taipei",
      "country": "TW",
      "postalCode": "100"
    }
  }
}
```

## Consumer Requirements

- Prefetch count: 10
- Acknowledgment: Manual (BasicAck after successful processing)
- Reject policy: Requeue once, then route to DLQ
- Concurrency: 3 consumers per instance
- Idempotency: Consumer MUST be idempotent (deduplicate by messageId)

## Dead Letter Queue

| DLQ | Source Queue | Routing |
|-----|-------------|---------|
| `shipping.dlq` | `shipping.queue` | x-dead-letter-exchange="" + x-dead-letter-routing-key="shipping.dlq" |

DLQ messages retain the original message headers plus:

| Header | Description |
|--------|-------------|
| `x-death` | RabbitMQ automatic death metadata |
| `x-original-routing-key` | Original routing key |
| `x-exception-message` | Error description |
| `x-retry-count` | Number of processing attempts |

## Exchange Configuration

```yaml
Exchange:
  name: shipping.exchange
  type: topic
  durable: true
  auto-delete: false

Queue:
  name: shipping.queue
  durable: true
  arguments:
    x-dead-letter-exchange: ""
    x-dead-letter-routing-key: shipping.dlq
    x-message-ttl: 86400000  # 24 hours

DeadLetterQueue:
  name: shipping.dlq
  durable: true

Binding:
  exchange: shipping.exchange
  queue: shipping.queue
  routing-key: shipping.create
```

## Serialization

- Content type: `application/json`
- Encoding: UTF-8
- Serializer: Jackson (Spring AMQP MessageConverter)
