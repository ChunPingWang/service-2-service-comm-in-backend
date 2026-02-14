# Implementation Plan: Service-to-Service Communication PoC

**Branch**: `001-s2s-comm-poc` | **Date**: 2026-02-14 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-s2s-comm-poc/spec.md`

## Summary

Build a microservice PoC validating 8 service-to-service communication
patterns (REST, gRPC, GraphQL, Kafka, RabbitMQ, API Gateway, Service
Discovery, Circuit Breaker) using a simplified e-commerce domain
(Order, Product, Payment, Notification, Shipping). Each service follows
Hexagonal Architecture with strict layer isolation. All services deploy
to a local Kind Kubernetes cluster with full observability
(OpenTelemetry, Jaeger, Prometheus, Grafana, Loki). TDD and BDD drive
all implementation with Testcontainers for integration tests and
ArchUnit for architecture enforcement.

## Technical Context

**Language/Version**: Java 23
**Primary Dependencies**: Spring Boot 4, Spring WebFlux/MVC,
  grpc-spring-boot-starter 3.2.0, Spring for GraphQL, Spring Kafka,
  Spring AMQP, Resilience4j 2.3.0, Spring Cloud 2024.0.0
**Storage**: N/A (in-memory/ephemeral for PoC)
**Testing**: JUnit 5, Testcontainers 1.20.4, ArchUnit, Mockito
**Target Platform**: Local Kind Kubernetes cluster (3 nodes)
**Project Type**: Multi-module Maven project (5 services + 1 E2E test
  module + shared proto)
**Performance Goals**: Functional correctness only (no load testing);
  Kafka/RabbitMQ event delivery within 5 seconds
**Constraints**: All components run in Kind; 16GB RAM / 4 CPU minimum;
  no cloud service dependencies; Docker 24+ required
**Scale/Scope**: 5 microservices, 8 communication patterns, 14
  functional verification scenarios

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Evidence |
|---|-----------|--------|----------|
| I | Hexagonal Architecture | PASS | Each service uses `adapter/in/`, `adapter/out/`, `application/port/in/`, `application/port/out/`, `application/service/`, `domain/model/`, `domain/event/`, `config/`. All frameworks in adapter/config only. |
| II | Layer Isolation & Dependency Inversion | PASS | Ports defined in `application/port/`. Adapters implement ports. Mappers in adapter packages convert between layers. No reverse dependencies. |
| III | SOLID Principles | PASS | Granular port interfaces (e.g., `ProductQueryPort`, `CreateOrderUseCase`). Each adapter is single-responsibility. New protocols addable without modifying use cases. |
| IV | Domain-Driven Design | PASS | 5 bounded contexts (Order, Product, Payment, Notification, Shipping). Domain events model state transitions. Value objects for IDs and Money. Ubiquitous language from PRD. |
| V | Test-Driven Development | PASS | TDD Red-Green-Refactor enforced. Unit tests for domain/application, Testcontainers integration tests for adapters, E2E tests in Kind, ArchUnit for architecture. |
| VI | Behavior-Driven Development | PASS | All 8 user stories have Given-When-Then acceptance scenarios. Edge cases captured as scenarios. |
| VII | Code Quality Standards | PASS | ArchUnit enforces layer rules. Static analysis via Maven plugins. Java records for immutable domain objects. Coverage targets set at 80%+ for domain/application. |

**Gate result**: ALL PASS — proceed to Phase 0.

## Project Structure

### Documentation (this feature)

```text
specs/001-s2s-comm-poc/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── rest-api.yaml        # OpenAPI specs for REST endpoints
│   ├── product.proto        # gRPC Protobuf definitions
│   ├── graphql-schema.graphqls  # GraphQL schema
│   ├── kafka-events.md      # Kafka event schemas
│   └── rabbitmq-messages.md # RabbitMQ message schemas
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
services/
├── order-service/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/poc/order/
│       │   ├── adapter/
│       │   │   ├── in/
│       │   │   │   ├── rest/
│       │   │   │   │   ├── OrderController.java
│       │   │   │   │   ├── OrderRequest.java
│       │   │   │   │   ├── OrderResponse.java
│       │   │   │   │   └── OrderRestMapper.java
│       │   │   │   └── graphql/
│       │   │   │       ├── OrderGraphQLController.java
│       │   │   │       └── OrderGraphQLMapper.java
│       │   │   └── out/
│       │   │       ├── grpc/
│       │   │       │   ├── ProductGrpcClient.java
│       │   │       │   └── ProductGrpcMapper.java
│       │   │       ├── rest/
│       │   │       │   ├── PaymentRestClient.java
│       │   │       │   └── PaymentRestMapper.java
│       │   │       └── messaging/
│       │   │           ├── OrderEventPublisher.java
│       │   │           └── OrderEventMapper.java
│       │   ├── application/
│       │   │   ├── port/
│       │   │   │   ├── in/
│       │   │   │   │   ├── CreateOrderUseCase.java
│       │   │   │   │   └── QueryOrderUseCase.java
│       │   │   │   └── out/
│       │   │   │       ├── ProductQueryPort.java
│       │   │   │       ├── PaymentPort.java
│       │   │   │       └── OrderEventPort.java
│       │   │   └── service/
│       │   │       └── OrderApplicationService.java
│       │   ├── domain/
│       │   │   ├── model/
│       │   │   │   ├── Order.java
│       │   │   │   ├── OrderId.java
│       │   │   │   ├── OrderStatus.java
│       │   │   │   └── OrderItem.java
│       │   │   └── event/
│       │   │       └── OrderCreatedEvent.java
│       │   └── config/
│       │       ├── CircuitBreakerConfig.java
│       │       ├── GrpcClientConfig.java
│       │       └── ObservabilityConfig.java
│       └── test/java/com/poc/order/
│           ├── unit/
│           │   ├── domain/OrderTest.java
│           │   └── application/OrderApplicationServiceTest.java
│           ├── integration/
│           │   ├── OrderRestIntegrationTest.java
│           │   ├── OrderGrpcIntegrationTest.java
│           │   └── OrderKafkaIntegrationTest.java
│           └── architecture/
│               └── HexagonalArchitectureTest.java
│
├── product-service/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/poc/product/
│       │   ├── adapter/
│       │   │   ├── in/
│       │   │   │   ├── grpc/
│       │   │   │   │   ├── ProductGrpcService.java
│       │   │   │   │   └── ProductGrpcMapper.java
│       │   │   │   ├── rest/
│       │   │   │   │   ├── ProductController.java
│       │   │   │   │   └── ProductRestMapper.java
│       │   │   │   └── graphql/
│       │   │   │       ├── ProductGraphQLResolver.java
│       │   │   │       └── ProductGraphQLMapper.java
│       │   │   └── out/ (empty for PoC)
│       │   ├── application/
│       │   │   ├── port/in/
│       │   │   │   ├── ProductQueryUseCase.java
│       │   │   │   └── InventoryCheckUseCase.java
│       │   │   └── service/
│       │   │       └── ProductApplicationService.java
│       │   ├── domain/
│       │   │   └── model/
│       │   │       ├── Product.java
│       │   │       ├── ProductId.java
│       │   │       └── Category.java
│       │   └── config/
│       │       └── GrpcServerConfig.java
│       └── test/java/com/poc/product/
│           ├── unit/
│           ├── integration/
│           └── architecture/
│
├── payment-service/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/poc/payment/
│       │   ├── adapter/
│       │   │   ├── in/rest/
│       │   │   │   ├── PaymentController.java
│       │   │   │   ├── PaymentRequest.java
│       │   │   │   ├── PaymentResponse.java
│       │   │   │   └── PaymentRestMapper.java
│       │   │   └── out/messaging/
│       │   │       ├── PaymentEventPublisher.java
│       │   │       └── PaymentEventMapper.java
│       │   ├── application/
│       │   │   ├── port/
│       │   │   │   ├── in/ProcessPaymentUseCase.java
│       │   │   │   └── out/PaymentEventPort.java
│       │   │   └── service/
│       │   │       └── PaymentApplicationService.java
│       │   ├── domain/
│       │   │   ├── model/
│       │   │   │   ├── Payment.java
│       │   │   │   ├── PaymentId.java
│       │   │   │   ├── PaymentStatus.java
│       │   │   │   └── Money.java
│       │   │   └── event/
│       │   │       └── PaymentCompletedEvent.java
│       │   └── config/
│       │       └── FaultSimulationConfig.java
│       └── test/java/com/poc/payment/
│           ├── unit/
│           ├── integration/
│           └── architecture/
│
├── notification-service/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/poc/notification/
│       │   ├── adapter/
│       │   │   ├── in/messaging/
│       │   │   │   ├── KafkaNotificationConsumer.java
│       │   │   │   └── KafkaEventMapper.java
│       │   │   └── out/messaging/
│       │   │       ├── RabbitMQPublisher.java
│       │   │       └── RabbitMQMessageMapper.java
│       │   ├── application/
│       │   │   ├── port/
│       │   │   │   ├── in/HandlePaymentEventUseCase.java
│       │   │   │   └── out/ShippingNotificationPort.java
│       │   │   └── service/
│       │   │       └── NotificationApplicationService.java
│       │   ├── domain/
│       │   │   └── model/
│       │   │       ├── Notification.java
│       │   │       ├── NotificationId.java
│       │   │       └── NotificationType.java
│       │   └── config/
│       │       ├── KafkaConsumerConfig.java
│       │       └── RabbitMQConfig.java
│       └── test/java/com/poc/notification/
│           ├── unit/
│           ├── integration/
│           └── architecture/
│
└── shipping-service/
    ├── pom.xml
    └── src/
        ├── main/java/com/poc/shipping/
        │   ├── adapter/
        │   │   └── in/messaging/
        │   │       ├── RabbitMQShippingConsumer.java
        │   │       └── ShippingMessageMapper.java
        │   ├── application/
        │   │   ├── port/in/
        │   │   │   └── ArrangeShipmentUseCase.java
        │   │   └── service/
        │   │       └── ShippingApplicationService.java
        │   ├── domain/
        │   │   └── model/
        │   │       ├── Shipment.java
        │   │       ├── ShipmentId.java
        │   │       └── ShipmentStatus.java
        │   └── config/
        │       └── RabbitMQConfig.java
        └── test/java/com/poc/shipping/
            ├── unit/
            ├── integration/
            └── architecture/

proto/
└── product.proto                     # Shared gRPC Protobuf definitions

e2e-tests/
├── pom.xml
└── src/test/java/com/poc/e2e/
    ├── SyncCommunicationE2ETest.java
    ├── AsyncCommunicationE2ETest.java
    ├── CircuitBreakerE2ETest.java
    ├── TracingE2ETest.java
    └── GatewayE2ETest.java

infrastructure/
├── kind/
│   ├── kind-cluster.yaml
│   └── setup.sh
├── k8s/
│   ├── namespaces.yaml
│   ├── api-gateway/
│   │   ├── apisix-deployment.yaml
│   │   └── apisix-routes.yaml
│   ├── service-mesh/
│   │   ├── istio-install.yaml
│   │   └── traffic-rules.yaml
│   ├── observability/
│   │   ├── otel-collector.yaml
│   │   ├── jaeger.yaml
│   │   ├── prometheus.yaml
│   │   ├── grafana.yaml
│   │   └── loki.yaml
│   ├── messaging/
│   │   ├── kafka.yaml
│   │   └── rabbitmq.yaml
│   ├── service-discovery/
│   │   ├── eureka.yaml
│   │   └── consul.yaml
│   └── services/
│       ├── order-service.yaml
│       ├── product-service.yaml
│       ├── payment-service.yaml
│       ├── notification-service.yaml
│       └── shipping-service.yaml
└── docker/
    └── Dockerfile.service            # Multi-stage build template

docker-compose.yml                    # Local dev (non-K8s)
kind-config.yaml                      # Kind cluster config
Makefile                              # Build/deploy automation
pom.xml                               # Parent POM (multi-module)
```

**Structure Decision**: Multi-module Maven project with 5 independent
service modules, 1 shared proto module, and 1 E2E test module. Each
service follows the Hexagonal Architecture directory convention from the
constitution. Infrastructure configurations (K8s manifests, Docker) are
co-located in the `infrastructure/` directory. This structure aligns with
the TECH.md specification and constitution requirements.

## Complexity Tracking

> No constitution violations identified. All patterns are justified by
> the PoC's explicit goal of validating multiple communication patterns.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 5 service modules | PRD requires 5 bounded contexts to demonstrate realistic S2S communication | Fewer services would not cover the required communication topology (sync + async + mesh) |
| Multiple communication protocols per service | PRD explicitly requires comparing REST vs gRPC vs GraphQL and Kafka vs RabbitMQ | Single protocol would not fulfill the PoC's comparison objective |
