# Quickstart Guide

## Prerequisites

| Tool | Version | Verification |
|------|---------|-------------|
| Java | 23 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker | 24+ | `docker version` |
| Kind | 0.24+ | `kind version` |
| kubectl | 1.28+ | `kubectl version --client` |
| grpcurl | latest | `grpcurl -version` (optional, for manual gRPC testing) |

**System Requirements**: 16GB RAM minimum (32GB recommended), 4 CPU cores

## Quick Start (One Command)

```bash
# Clone, build, deploy, and run all tests
make all
```

This will:
1. Create a Kind cluster (3 nodes)
2. Build all 5 services
3. Build Docker images and load into Kind
4. Deploy infrastructure (Kafka, RabbitMQ, APISIX, observability stack)
5. Deploy all services
6. Run E2E tests

## Step-by-Step Setup

### 1. Create Kind Cluster

```bash
make cluster-up
```

Verify:
```bash
kubectl get nodes
# Expected: 3 nodes (1 control-plane, 2 workers) in Ready state
```

### 2. Build Services

```bash
make build
```

This compiles all Java services and builds Docker images.

### 3. Deploy Infrastructure

```bash
make deploy-infra
```

Deploys: Kafka, RabbitMQ, APISIX, OpenTelemetry Collector, Jaeger,
Prometheus, Grafana, Loki.

Verify:
```bash
kubectl get pods -n poc -l tier=infrastructure
# All pods should be Running
```

### 4. Deploy Services

```bash
make deploy-services
```

Deploys: Order, Product, Payment, Notification, Shipping services.

Verify:
```bash
kubectl get pods -n poc -l tier=application
# All 5 service pods should be Running
```

### 5. Run Tests

```bash
# Unit tests only
make test-unit

# Integration tests (requires Docker for Testcontainers)
make test-integration

# E2E tests (requires running Kind cluster with deployed services)
make test-e2e

# All tests
make test
```

## Accessing UIs

| Service | URL | Purpose |
|---------|-----|---------|
| API Gateway | http://localhost:30080 | Main entry point |
| Jaeger | http://localhost:16686 | Distributed tracing |
| Grafana | http://localhost:3000 | Metrics dashboards |
| Prometheus | http://localhost:9090 | Raw metrics |

## Manual Verification

### REST (Order Service)

```bash
# Create an order
curl -X POST http://localhost:30080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"productId": "prod-001", "quantity": 2, "customerId": "cust-001"}'

# Expected: 201 Created with order JSON
```

### GraphQL (Product Service)

```bash
curl -X POST http://localhost:30080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"query": "{ products(category: \"ELECTRONICS\", limit: 5) { id name price stock } }"}'

# Expected: 200 OK with products array
```

### gRPC (Product Service)

```bash
grpcurl -plaintext localhost:30080 \
  com.poc.product.ProductService/GetProduct \
  -d '{"product_id": "prod-001"}'

# Expected: Product JSON with name, price, stock
```

### Rate Limiting

```bash
# Send 200 rapid requests to trigger rate limiting
for i in $(seq 1 200); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    http://localhost:30080/api/v1/orders \
    -H "Authorization: Bearer <JWT_TOKEN>"
done | sort | uniq -c

# Expected: Some requests return 429
```

### Circuit Breaker

```bash
# Enable fault simulation on Payment Service
kubectl set env deployment/payment-service \
  FAULT_SIMULATION_ENABLED=true \
  FAULT_FAILURE_RATE=100 -n poc

# Create orders to trigger circuit breaker
for i in $(seq 1 15); do
  curl -X POST http://localhost:30080/api/v1/orders \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer <JWT_TOKEN>" \
    -d "{\"productId\": \"prod-001\", \"quantity\": 1, \"customerId\": \"cust-$i\"}"
done

# Expected: After ~10 failures, fallback responses appear

# Disable fault simulation
kubectl set env deployment/payment-service \
  FAULT_SIMULATION_ENABLED=false -n poc
```

### Distributed Tracing

1. Execute a full order flow (create order)
2. Open Jaeger UI: http://localhost:16686
3. Select service: `order-service`
4. Click "Find Traces"
5. Verify trace spans across Order, Product, Payment services

## Cleanup

```bash
make clean
```

This deletes the Kind cluster and cleans Maven build artifacts.

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Kind cluster won't start | Ensure Docker is running; check available RAM (need 6.5GB+) |
| Pods stuck in Pending | Check node resources: `kubectl describe nodes` |
| Service can't connect to Kafka | Wait for Kafka pod to be Ready before deploying services |
| gRPC connection refused | Verify product-service exposes port 9090 |
| 401 on all requests | Generate a valid JWT token for testing |
| Tests timeout | Increase Testcontainers startup timeout in test config |
