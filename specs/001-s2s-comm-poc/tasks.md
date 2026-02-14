# Tasks: Service-to-Service Communication PoC

**Input**: Design documents from `/specs/001-s2s-comm-poc/`
**Prerequisites**: plan.md, spec.md, data-model.md, contracts/, research.md, quickstart.md
**Tests**: MANDATORY per constitution (TDD is NON-NEGOTIABLE). All tests written FIRST, verified FAILING before implementation.
**Organization**: Tasks grouped by user story for independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Paths are relative to repository root

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Maven multi-module project structure, build tooling, and deployment infrastructure

- [ ] T001 Create parent POM with module declarations and dependency management in pom.xml
- [ ] T002 [P] Create Kind cluster configuration in infrastructure/kind/kind-cluster.yaml
- [ ] T003 [P] Create Makefile with targets: build, test, cluster-up, deploy-infra, deploy-services, test-e2e, clean
- [ ] T004 [P] Create Docker multi-stage build template in infrastructure/docker/Dockerfile.service
- [ ] T005 [P] Create Kubernetes namespace manifest in infrastructure/k8s/namespaces.yaml
- [ ] T006 [P] Create shared proto module with product.proto and pom.xml in proto/

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Service module skeletons, ArchUnit architecture enforcement, and base configuration that ALL user stories depend on

**CRITICAL**: No user story work can begin until this phase is complete

### Service Module Skeletons

- [ ] T007 [P] Create Order Service module skeleton with pom.xml and package directories in services/order-service/
- [ ] T008 [P] Create Product Service module skeleton with pom.xml and package directories in services/product-service/
- [ ] T009 [P] Create Payment Service module skeleton with pom.xml and package directories in services/payment-service/
- [ ] T010 [P] Create Notification Service module skeleton with pom.xml and package directories in services/notification-service/
- [ ] T011 [P] Create Shipping Service module skeleton with pom.xml and package directories in services/shipping-service/
- [ ] T012 [P] Create E2E test module skeleton with pom.xml in e2e-tests/

### Architecture Enforcement (TDD: Write ArchUnit tests FIRST)

- [ ] T013 [P] Write ArchUnit hexagonal architecture test in services/order-service/src/test/java/com/poc/order/architecture/HexagonalArchitectureTest.java
- [ ] T014 [P] Write ArchUnit hexagonal architecture test in services/product-service/src/test/java/com/poc/product/architecture/HexagonalArchitectureTest.java
- [ ] T015 [P] Write ArchUnit hexagonal architecture test in services/payment-service/src/test/java/com/poc/payment/architecture/HexagonalArchitectureTest.java
- [ ] T016 [P] Write ArchUnit hexagonal architecture test in services/notification-service/src/test/java/com/poc/notification/architecture/HexagonalArchitectureTest.java
- [ ] T017 [P] Write ArchUnit hexagonal architecture test in services/shipping-service/src/test/java/com/poc/shipping/architecture/HexagonalArchitectureTest.java

### Base Configuration

- [ ] T018 [P] Create Spring Boot application.yml with server port, application name, and actuator config for all 5 services
- [ ] T019 [P] Create Spring Boot main application class for each service (OrderApplication, ProductApplication, PaymentApplication, NotificationApplication, ShippingApplication)

**Checkpoint**: Foundation ready -- all modules compile, ArchUnit tests exist (will fail until domain classes are added), services start as empty Spring Boot apps

---

## Phase 3: User Story 1 -- Synchronous Service Communication (Priority: P1) MVP

**Goal**: Validate REST, gRPC, and GraphQL communication between Order, Product, and Payment services

**Independent Test**: Send HTTP/gRPC/GraphQL requests between services and verify correct responses. Test each protocol independently.

### TDD: Domain Model Tests (Write FIRST, verify FAIL)

- [ ] T020 [P] [US1] Write unit tests for Order aggregate root (creation, state transitions, validation) in services/order-service/src/test/java/com/poc/order/unit/domain/OrderTest.java
- [ ] T021 [P] [US1] Write unit tests for Product aggregate root (creation, inventory check, stock reservation) in services/product-service/src/test/java/com/poc/product/unit/domain/ProductTest.java
- [ ] T022 [P] [US1] Write unit tests for Payment aggregate root (creation, complete, fail, state transitions) in services/payment-service/src/test/java/com/poc/payment/unit/domain/PaymentTest.java

### Domain Model Implementation

- [ ] T023 [P] [US1] Implement Order, OrderId, CustomerId, OrderItem, OrderStatus, Money in services/order-service/src/main/java/com/poc/order/domain/model/
- [ ] T024 [P] [US1] Implement Product, ProductId, Category, Money in services/product-service/src/main/java/com/poc/product/domain/model/
- [ ] T025 [P] [US1] Implement Payment, PaymentId, PaymentStatus, OrderId, Money in services/payment-service/src/main/java/com/poc/payment/domain/model/

### TDD: Application Service Tests (Write FIRST, verify FAIL)

- [ ] T026 [P] [US1] Write unit tests for OrderApplicationService (create order, query order, inventory check via port) in services/order-service/src/test/java/com/poc/order/unit/application/OrderApplicationServiceTest.java
- [ ] T027 [P] [US1] Write unit tests for ProductApplicationService (query product, check inventory) in services/product-service/src/test/java/com/poc/product/unit/application/ProductApplicationServiceTest.java
- [ ] T028 [P] [US1] Write unit tests for PaymentApplicationService (process payment) in services/payment-service/src/test/java/com/poc/payment/unit/application/PaymentApplicationServiceTest.java

### Application Layer Implementation (Ports & Services)

- [ ] T029 [P] [US1] Create Order Service inbound ports (CreateOrderUseCase, QueryOrderUseCase) in services/order-service/src/main/java/com/poc/order/application/port/in/
- [ ] T030 [P] [US1] Create Order Service outbound ports (ProductQueryPort, PaymentPort) in services/order-service/src/main/java/com/poc/order/application/port/out/
- [ ] T031 [P] [US1] Create Product Service inbound ports (ProductQueryUseCase, InventoryCheckUseCase) in services/product-service/src/main/java/com/poc/product/application/port/in/
- [ ] T032 [P] [US1] Create Payment Service inbound port (ProcessPaymentUseCase) in services/payment-service/src/main/java/com/poc/payment/application/port/in/
- [ ] T033 [US1] Implement OrderApplicationService in services/order-service/src/main/java/com/poc/order/application/service/OrderApplicationService.java
- [ ] T034 [P] [US1] Implement ProductApplicationService in services/product-service/src/main/java/com/poc/product/application/service/ProductApplicationService.java
- [ ] T035 [P] [US1] Implement PaymentApplicationService in services/payment-service/src/main/java/com/poc/payment/application/service/PaymentApplicationService.java

### TDD: Integration Tests for Adapters (Write FIRST, verify FAIL)

- [ ] T036 [P] [US1] Write REST integration test for Order Service in services/order-service/src/test/java/com/poc/order/integration/OrderRestIntegrationTest.java
- [ ] T037 [P] [US1] Write gRPC integration test for Product Service in services/product-service/src/test/java/com/poc/product/integration/ProductGrpcIntegrationTest.java
- [ ] T038 [P] [US1] Write GraphQL integration test for Product Service in services/product-service/src/test/java/com/poc/product/integration/ProductGraphQLIntegrationTest.java
- [ ] T039 [P] [US1] Write REST integration test for Payment Service in services/payment-service/src/test/java/com/poc/payment/integration/PaymentRestIntegrationTest.java
- [ ] T040 [P] [US1] Write gRPC client integration test (Order-to-Product) in services/order-service/src/test/java/com/poc/order/integration/OrderGrpcIntegrationTest.java

### Adapter Implementation -- Product Service (gRPC Server + GraphQL + REST)

- [ ] T041 [P] [US1] Implement ProductGrpcService and ProductGrpcMapper (inbound gRPC adapter) in services/product-service/src/main/java/com/poc/product/adapter/in/grpc/
- [ ] T042 [P] [US1] Implement ProductGraphQLResolver and ProductGraphQLMapper in services/product-service/src/main/java/com/poc/product/adapter/in/graphql/
- [ ] T043 [P] [US1] Implement ProductController and ProductRestMapper in services/product-service/src/main/java/com/poc/product/adapter/in/rest/
- [ ] T044 [US1] Create GraphQL schema file in services/product-service/src/main/resources/graphql/schema.graphqls
- [ ] T045 [US1] Configure gRPC server in services/product-service/src/main/java/com/poc/product/config/GrpcServerConfig.java

### Adapter Implementation -- Order Service (REST Controller + gRPC Client + REST Client)

- [ ] T046 [US1] Implement OrderController, OrderRequest, OrderResponse, OrderRestMapper in services/order-service/src/main/java/com/poc/order/adapter/in/rest/
- [ ] T047 [US1] Implement ProductGrpcClient and ProductGrpcMapper (outbound gRPC adapter) in services/order-service/src/main/java/com/poc/order/adapter/out/grpc/
- [ ] T048 [US1] Implement PaymentRestClient and PaymentRestMapper (outbound REST adapter) in services/order-service/src/main/java/com/poc/order/adapter/out/rest/
- [ ] T049 [US1] Configure gRPC client in services/order-service/src/main/java/com/poc/order/config/GrpcClientConfig.java

### Adapter Implementation -- Payment Service (REST Controller)

- [ ] T050 [US1] Implement PaymentController, PaymentRequest, PaymentResponse, PaymentRestMapper in services/payment-service/src/main/java/com/poc/payment/adapter/in/rest/

### Kubernetes Deployment Manifests for US1 Services

- [ ] T051 [P] [US1] Create K8s deployment manifest for Order Service in infrastructure/k8s/services/order-service.yaml
- [ ] T052 [P] [US1] Create K8s deployment manifest for Product Service in infrastructure/k8s/services/product-service.yaml
- [ ] T053 [P] [US1] Create K8s deployment manifest for Payment Service in infrastructure/k8s/services/payment-service.yaml

**Checkpoint**: REST, gRPC, and GraphQL communication works between Order, Product, and Payment. Each protocol independently verifiable. ArchUnit tests pass.

---

## Phase 4: User Story 2 -- Asynchronous Event-Driven Communication (Priority: P1)

**Goal**: Validate Kafka event streaming and RabbitMQ message queuing across Payment, Notification, and Shipping services

**Independent Test**: Publish events to Kafka/RabbitMQ and verify consumers receive and process them. Verify DLQ routing on failure.

### TDD: Domain Model Tests (Write FIRST, verify FAIL)

- [ ] T054 [P] [US2] Write unit tests for OrderCreatedEvent in services/order-service/src/test/java/com/poc/order/unit/domain/OrderCreatedEventTest.java
- [ ] T055 [P] [US2] Write unit tests for PaymentCompletedEvent in services/payment-service/src/test/java/com/poc/payment/unit/domain/PaymentCompletedEventTest.java
- [ ] T056 [P] [US2] Write unit tests for Notification aggregate root (creation, markSent, markFailed) in services/notification-service/src/test/java/com/poc/notification/unit/domain/NotificationTest.java
- [ ] T057 [P] [US2] Write unit tests for Shipment aggregate root (creation, ship, deliver) in services/shipping-service/src/test/java/com/poc/shipping/unit/domain/ShipmentTest.java

### Domain Model Implementation

- [ ] T058 [P] [US2] Implement OrderCreatedEvent in services/order-service/src/main/java/com/poc/order/domain/event/OrderCreatedEvent.java
- [ ] T059 [P] [US2] Implement PaymentCompletedEvent in services/payment-service/src/main/java/com/poc/payment/domain/event/PaymentCompletedEvent.java
- [ ] T060 [P] [US2] Implement Notification, NotificationId, NotificationType, NotificationStatus, OrderId in services/notification-service/src/main/java/com/poc/notification/domain/model/
- [ ] T061 [P] [US2] Implement Shipment, ShipmentId, ShipmentStatus, OrderId in services/shipping-service/src/main/java/com/poc/shipping/domain/model/

### TDD: Application Service Tests (Write FIRST, verify FAIL)

- [ ] T062 [P] [US2] Write unit tests for NotificationApplicationService in services/notification-service/src/test/java/com/poc/notification/unit/application/NotificationApplicationServiceTest.java
- [ ] T063 [P] [US2] Write unit tests for ShippingApplicationService in services/shipping-service/src/test/java/com/poc/shipping/unit/application/ShippingApplicationServiceTest.java

### Application Layer Implementation

- [ ] T064 [P] [US2] Create Order Service outbound event port (OrderEventPort) in services/order-service/src/main/java/com/poc/order/application/port/out/OrderEventPort.java
- [ ] T065 [P] [US2] Create Payment Service ports (HandleOrderCreatedUseCase inbound, PaymentEventPort outbound) and update PaymentApplicationService for Kafka consume/produce in services/payment-service/src/main/java/com/poc/payment/application/
- [ ] T066 [P] [US2] Create Notification Service ports (HandlePaymentEventUseCase, ShippingNotificationPort) and NotificationApplicationService in services/notification-service/src/main/java/com/poc/notification/application/
- [ ] T067 [P] [US2] Create Shipping Service port (ArrangeShipmentUseCase) and ShippingApplicationService in services/shipping-service/src/main/java/com/poc/shipping/application/

### TDD: Integration Tests for Messaging Adapters (Write FIRST, verify FAIL)

- [ ] T068 [P] [US2] Write Kafka integration test for Order event publishing in services/order-service/src/test/java/com/poc/order/integration/OrderKafkaIntegrationTest.java
- [ ] T069 [P] [US2] Write Kafka integration test for Payment consume/produce in services/payment-service/src/test/java/com/poc/payment/integration/PaymentKafkaIntegrationTest.java
- [ ] T070 [P] [US2] Write Kafka + RabbitMQ integration test for Notification in services/notification-service/src/test/java/com/poc/notification/integration/NotificationMessagingIntegrationTest.java
- [ ] T071 [P] [US2] Write RabbitMQ integration test for Shipping consumer in services/shipping-service/src/test/java/com/poc/shipping/integration/ShippingRabbitMQIntegrationTest.java

### Adapter Implementation -- Kafka & RabbitMQ

- [ ] T072 [US2] Implement OrderEventPublisher and OrderEventMapper (Kafka producer) in services/order-service/src/main/java/com/poc/order/adapter/out/messaging/
- [ ] T073 [US2] Implement KafkaOrderConsumer and PaymentEventPublisher with PaymentEventMapper in services/payment-service/src/main/java/com/poc/payment/adapter/
- [ ] T074 [US2] Implement KafkaNotificationConsumer, KafkaEventMapper, RabbitMQPublisher, RabbitMQMessageMapper in services/notification-service/src/main/java/com/poc/notification/adapter/
- [ ] T075 [US2] Implement RabbitMQShippingConsumer and ShippingMessageMapper in services/shipping-service/src/main/java/com/poc/shipping/adapter/in/messaging/

### Messaging Infrastructure Configuration

- [ ] T076 [P] [US2] Configure Kafka consumer/producer in services/payment-service/src/main/java/com/poc/payment/config/ and application.yml
- [ ] T077 [P] [US2] Configure Kafka consumer and RabbitMQ producer in services/notification-service/src/main/java/com/poc/notification/config/
- [ ] T078 [P] [US2] Configure RabbitMQ consumer in services/shipping-service/src/main/java/com/poc/shipping/config/RabbitMQConfig.java

### Kubernetes Infrastructure for Messaging

- [ ] T079 [P] [US2] Create Kafka deployment manifest in infrastructure/k8s/messaging/kafka.yaml
- [ ] T080 [P] [US2] Create RabbitMQ deployment manifest in infrastructure/k8s/messaging/rabbitmq.yaml
- [ ] T081 [P] [US2] Create K8s deployment manifests for Notification and Shipping services in infrastructure/k8s/services/

### Shipping-to-Order Feedback Event (Order SHIPPED Trigger)

- [ ] T082 [P] [US2] Write unit test for ShipmentArrangedEvent in services/shipping-service/src/test/java/com/poc/shipping/unit/domain/ShipmentArrangedEventTest.java
- [ ] T083 [P] [US2] Implement ShipmentArrangedEvent in services/shipping-service/src/main/java/com/poc/shipping/domain/event/ShipmentArrangedEvent.java
- [ ] T084 [US2] Create ShipmentEventPort and update ShippingApplicationService to publish ShipmentArrangedEvent after shipment arranged in services/shipping-service/src/main/java/com/poc/shipping/application/
- [ ] T085 [P] [US2] Write Kafka integration test for Shipping event publish and Order consume in services/shipping-service/src/test/java/com/poc/shipping/integration/ShipmentKafkaIntegrationTest.java
- [ ] T086 [US2] Implement ShipmentEventPublisher and ShipmentEventMapper (Kafka producer) in services/shipping-service/src/main/java/com/poc/shipping/adapter/out/messaging/
- [ ] T087 [US2] Create HandleShipmentEventUseCase port and implement KafkaShipmentConsumer with ShipmentEventMapper in services/order-service/src/main/java/com/poc/order/ (application/port/in/ + adapter/in/messaging/)
- [ ] T088 [US2] Configure Kafka producer in services/shipping-service/src/main/java/com/poc/shipping/config/KafkaProducerConfig.java and application.yml

### DLQ Configuration (Explicit)

- [ ] T089 [P] [US2] Configure Kafka DLQ error handling (DefaultErrorHandler + DeadLetterPublishingRecoverer) for Payment and Notification consumers in respective config/ packages and application.yml
- [ ] T090 [P] [US2] Configure RabbitMQ DLQ (x-dead-letter-exchange, x-dead-letter-routing-key) for Shipping consumer in services/shipping-service/src/main/java/com/poc/shipping/config/RabbitMQConfig.java
- [ ] T091 [P] [US2] Write DLQ verification integration test (Kafka + RabbitMQ) in e2e-tests/src/test/java/com/poc/e2e/DlqVerificationTest.java

**Checkpoint**: Kafka events flow Order->Payment->Notification. RabbitMQ messages flow Notification->Shipping. Shipping publishes ShipmentArrangedEvent back to Order (SHIPPED status). DLQ routing verified for all 3 Kafka topics and RabbitMQ queue. All messaging integration tests pass with Testcontainers.

---

## Phase 5: User Story 3 -- API Gateway & Traffic Management (Priority: P2)

**Goal**: Validate APISIX API Gateway with unified routing, rate limiting, and JWT authentication

**Independent Test**: Send requests through gateway and verify routing, rate limit enforcement (429), and JWT auth checks (401).

### TDD: Gateway Integration Tests (Write FIRST, verify FAIL)

- [ ] T092 [P] [US3] Write gateway routing integration test in e2e-tests/src/test/java/com/poc/e2e/GatewayRoutingTest.java
- [ ] T093 [P] [US3] Write rate limiting integration test in e2e-tests/src/test/java/com/poc/e2e/RateLimitingTest.java
- [ ] T094 [P] [US3] Write JWT authentication integration test in e2e-tests/src/test/java/com/poc/e2e/JwtAuthenticationTest.java

### Gateway Configuration

- [ ] T095 [US3] Create APISIX deployment manifest in infrastructure/k8s/api-gateway/apisix-deployment.yaml
- [ ] T096 [US3] Create APISIX route configuration with upstream definitions for all services in infrastructure/k8s/api-gateway/apisix-routes.yaml
- [ ] T097 [US3] Configure JWT authentication plugin for protected routes in APISIX routes
- [ ] T098 [US3] Configure rate limiting plugin (limit-req, 100 req/s) in APISIX routes

**Checkpoint**: All external requests route through APISIX. Rate limiting returns 429. JWT auth rejects unauthorized requests with 401. gRPC proxying works for Product Service.

---

## Phase 6: User Story 4 -- Service Discovery (Priority: P2)

**Goal**: Validate dynamic service registration and discovery via Kubernetes DNS, Eureka, and Consul

**Independent Test**: Register services dynamically, query registry, verify discoverability within 30s and deregistration within 60s.

### TDD: Discovery Integration Tests (Write FIRST, verify FAIL)

- [ ] T099 [P] [US4] Write K8s DNS service discovery test in e2e-tests/src/test/java/com/poc/e2e/KubernetesDnsDiscoveryTest.java
- [ ] T100 [P] [US4] Write Eureka service discovery test in e2e-tests/src/test/java/com/poc/e2e/EurekaDiscoveryTest.java
- [ ] T101 [P] [US4] Write Consul service discovery test in e2e-tests/src/test/java/com/poc/e2e/ConsulDiscoveryTest.java

### Service Discovery Infrastructure

- [ ] T102 [P] [US4] Create Eureka server deployment manifest in infrastructure/k8s/service-discovery/eureka.yaml
- [ ] T103 [P] [US4] Create Consul deployment manifest in infrastructure/k8s/service-discovery/consul.yaml
- [ ] T104 [US4] Add Spring Cloud discovery client configuration (K8s DNS, Eureka, Consul profiles) to all service application.yml files

**Checkpoint**: Services register automatically on startup, are discoverable via all three mechanisms, and deregister on shutdown within the specified time windows.

---

## Phase 7: User Story 5 -- Distributed Tracing & Observability (Priority: P2)

**Goal**: Validate end-to-end distributed tracing, centralized metrics, and log aggregation across all services

**Independent Test**: Trigger multi-service request flow and verify complete traces in Jaeger, metrics in Prometheus/Grafana, and correlated logs in Loki.

### TDD: Observability Integration Tests (Write FIRST, verify FAIL)

- [ ] T105 [P] [US5] Write distributed tracing verification test in e2e-tests/src/test/java/com/poc/e2e/TracingE2ETest.java
- [ ] T106 [P] [US5] Write metrics collection verification test in e2e-tests/src/test/java/com/poc/e2e/MetricsVerificationTest.java

### Observability Infrastructure

- [ ] T107 [P] [US5] Create OpenTelemetry Collector deployment manifest in infrastructure/k8s/observability/otel-collector.yaml
- [ ] T108 [P] [US5] Create Jaeger deployment manifest in infrastructure/k8s/observability/jaeger.yaml
- [ ] T109 [P] [US5] Create Prometheus deployment manifest with scrape config in infrastructure/k8s/observability/prometheus.yaml
- [ ] T110 [P] [US5] Create Grafana deployment manifest with dashboard provisioning in infrastructure/k8s/observability/grafana.yaml
- [ ] T111 [P] [US5] Create Loki + Promtail deployment manifests in infrastructure/k8s/observability/loki.yaml

### Service Instrumentation Configuration

- [ ] T112 [US5] Configure OpenTelemetry SDK, Micrometer tracing, and structured logging with correlation IDs in all service application.yml files
- [ ] T113 [P] [US5] Create ObservabilityConfig for each service in config/ packages (OTel exporter, Prometheus endpoint, log pattern with traceId/spanId)

**Checkpoint**: Single trace ID connects all service spans in Jaeger. Metrics visible in Grafana dashboards. Correlated logs queryable in Loki via correlation ID.

---

## Phase 8: User Story 6 -- Circuit Breaker & Resilience (Priority: P3)

**Goal**: Validate circuit breaker, retry, and fallback patterns using Resilience4j on Order-to-Payment path

**Independent Test**: Simulate Payment Service failures and verify circuit breaker opens after threshold, retries execute with backoff, and fallback responses are returned.

### TDD: Resilience Tests (Write FIRST, verify FAIL)

- [ ] T114 [P] [US6] Write circuit breaker unit test for Order-to-Payment path in services/order-service/src/test/java/com/poc/order/unit/application/CircuitBreakerTest.java
- [ ] T115 [P] [US6] Write circuit breaker integration test with Testcontainers in services/order-service/src/test/java/com/poc/order/integration/CircuitBreakerIntegrationTest.java

### Resilience Implementation

- [ ] T116 [US6] Configure Resilience4j circuit breaker, retry, and time limiter in services/order-service/src/main/java/com/poc/order/config/CircuitBreakerConfig.java
- [ ] T117 [US6] Add @CircuitBreaker and @Retry annotations with fallback methods to PaymentRestClient in services/order-service/src/main/java/com/poc/order/adapter/out/rest/PaymentRestClient.java
- [ ] T118 [US6] Implement FaultSimulationConfig for Payment Service in services/payment-service/src/main/java/com/poc/payment/config/FaultSimulationConfig.java
- [ ] T119 [US6] Add Resilience4j configuration properties (sliding window size: 10, failure threshold: 50%, wait duration: 10s) to Order Service application.yml

**Checkpoint**: Circuit breaker transitions CLOSED->OPEN->HALF-OPEN correctly. Retries execute with exponential backoff (1s base, 2x multiplier, 3 attempts). Fallback responses returned when circuit is open.

---

## Phase 9: User Story 7 -- Service Mesh (Priority: P3)

**Goal**: Validate Istio service mesh with sidecar proxy injection, mTLS, and traffic management rules

**Independent Test**: Deploy services with sidecar proxies, verify mTLS encryption, test traffic splitting (90/10) and fault injection rules.

### TDD: Service Mesh Tests (Write FIRST, verify FAIL)

- [ ] T120 [P] [US7] Write sidecar injection verification test in e2e-tests/src/test/java/com/poc/e2e/ServiceMeshSidecarTest.java
- [ ] T121 [P] [US7] Write mTLS verification test in e2e-tests/src/test/java/com/poc/e2e/MtlsVerificationTest.java
- [ ] T122 [P] [US7] Write traffic management test (canary, fault injection) in e2e-tests/src/test/java/com/poc/e2e/TrafficManagementTest.java

### Service Mesh Infrastructure

- [ ] T123 [US7] Create Istio installation manifest in infrastructure/k8s/service-mesh/istio-install.yaml
- [ ] T124 [US7] Configure namespace for automatic sidecar injection (label: istio-injection=enabled)
- [ ] T125 [US7] Create mTLS PeerAuthentication policy in infrastructure/k8s/service-mesh/mtls-policy.yaml
- [ ] T126 [US7] Create traffic management rules (VirtualService, DestinationRule for canary 90/10 split) in infrastructure/k8s/service-mesh/traffic-rules.yaml
- [ ] T127 [US7] Create fault injection rules (delay, abort) in infrastructure/k8s/service-mesh/fault-injection.yaml

**Checkpoint**: All traffic routes through sidecar proxies transparently. mTLS encrypts service-to-service communication. Traffic splitting and fault injection work as configured.

---

## Phase 10: User Story 8 -- End-to-End Business Flow (Priority: P3)

**Goal**: Execute complete order processing flow (product query -> order creation -> payment -> notification -> shipping) across all 5 services

**Independent Test**: Run full business flow and verify each step completes, tracing connects all 5 services, and circuit breaker handles failures gracefully.

### TDD: E2E Tests (Write FIRST, verify FAIL)

- [ ] T128 [P] [US8] Write synchronous communication E2E test in e2e-tests/src/test/java/com/poc/e2e/SyncCommunicationE2ETest.java
- [ ] T129 [P] [US8] Write asynchronous communication E2E test in e2e-tests/src/test/java/com/poc/e2e/AsyncCommunicationE2ETest.java
- [ ] T130 [P] [US8] Write full business flow E2E test (all 5 services) in e2e-tests/src/test/java/com/poc/e2e/FullBusinessFlowE2ETest.java
- [ ] T131 [P] [US8] Write circuit breaker E2E test in e2e-tests/src/test/java/com/poc/e2e/CircuitBreakerE2ETest.java
- [ ] T132 [US8] Write distributed tracing E2E test verifying single trace across all 5 services in e2e-tests/src/test/java/com/poc/e2e/DistributedTracingE2ETest.java

### E2E Integration

- [ ] T133 [US8] Create Kind cluster setup script with service deployment ordering in infrastructure/kind/setup.sh
- [ ] T134 [US8] Create docker-compose.yml for local development environment (non-K8s) at repository root

**Checkpoint**: Complete order flow executes successfully across all 5 services. Single trace ID connects all spans. Circuit breaker handles Payment Service failures gracefully in E2E scenario.

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: Final refinements that span multiple user stories

- [ ] T135 [P] Validate all ArchUnit tests pass across 5 services
- [ ] T136 [P] Verify test coverage meets 80% threshold for domain and application layers
- [ ] T137 Run quickstart.md validation (cluster-up, build, deploy, test full cycle)
- [ ] T138 Review and finalize Makefile targets for complete build-test-deploy cycle

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies -- start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 -- BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2 -- MVP, start first
- **US2 (Phase 4)**: Depends on Phase 2 -- can parallel with US1 (different services/adapters)
- **US3 (Phase 5)**: Depends on Phase 2 + US1 (needs running services to route to)
- **US4 (Phase 6)**: Depends on Phase 2 -- can parallel with US1/US2
- **US5 (Phase 7)**: Depends on Phase 2 + US1/US2 (needs services generating traces/metrics)
- **US6 (Phase 8)**: Depends on US1 (needs Order-to-Payment REST path)
- **US7 (Phase 9)**: Depends on Phase 2 + US1 (needs running services for mesh)
- **US8 (Phase 10)**: Depends on US1 + US2 + US5 + US6 (full flow needs all components)
- **Polish (Phase 11)**: Depends on all desired user stories being complete

### User Story Dependency Graph

```
Phase 1 (Setup)
  |
  v
Phase 2 (Foundational)
  |
  +---> US1 (Sync) ----------+---> US3 (Gateway)
  |                           +---> US6 (Circuit Breaker)
  |                           +---> US7 (Service Mesh)
  +---> US2 (Async) ----------+
  |                           +---> US5 (Observability)
  +---> US4 (Discovery)       |
  |                           +---> US8 (E2E) <-- depends on US1+US2+US5+US6
  |
  +---> Phase 11 (Polish) <-- after all stories
```

### Within Each User Story (TDD Cycle)

1. Write unit tests for domain models -> verify FAIL
2. Implement domain models -> verify PASS
3. Write unit tests for application services -> verify FAIL
4. Implement application ports and services -> verify PASS
5. Write integration tests for adapters -> verify FAIL
6. Implement adapters and mappers -> verify PASS
7. Verify ArchUnit tests still pass

### Parallel Opportunities

**Within Phase 2 (Foundational)**:
```
T007, T008, T009, T010, T011, T012 -- all service skeletons in parallel
T013, T014, T015, T016, T017 -- all ArchUnit tests in parallel
```

**Within US1 (Phase 3)**:
```
T020, T021, T022 -- domain model tests in parallel (different services)
T023, T024, T025 -- domain implementations in parallel
T026, T027, T028 -- application tests in parallel
T036, T037, T038, T039, T040 -- integration tests in parallel
T041, T042, T043 -- Product adapters in parallel
T051, T052, T053 -- K8s manifests in parallel
```

**Within US2 (Phase 4)**:
```
T054, T055, T056, T057 -- domain model tests in parallel
T058, T059, T060, T061 -- domain implementations in parallel
T062, T063 -- application tests in parallel
T068, T069, T070, T071 -- messaging integration tests in parallel
T079, T080, T081 -- K8s manifests in parallel
T082, T083 -- ShipmentArrangedEvent test + impl in parallel
T085 -- Shipping Kafka integration test (parallel with above)
T089, T090, T091 -- DLQ configuration tasks in parallel
```

**Cross-Story Parallel**:
```
US1 (Phase 3) and US2 (Phase 4) can proceed in parallel after Phase 2
US4 (Discovery) can proceed in parallel with US1/US2
```

---

## Parallel Example: User Story 1

```bash
# Launch all domain model tests in parallel:
Task: "Write Order domain tests in services/order-service/.../OrderTest.java"
Task: "Write Product domain tests in services/product-service/.../ProductTest.java"
Task: "Write Payment domain tests in services/payment-service/.../PaymentTest.java"

# Launch all domain implementations in parallel:
Task: "Implement Order domain in services/order-service/.../domain/model/"
Task: "Implement Product domain in services/product-service/.../domain/model/"
Task: "Implement Payment domain in services/payment-service/.../domain/model/"

# Launch all integration tests in parallel:
Task: "Write REST test in .../OrderRestIntegrationTest.java"
Task: "Write gRPC test in .../ProductGrpcIntegrationTest.java"
Task: "Write GraphQL test in .../ProductGraphQLIntegrationTest.java"
Task: "Write REST test in .../PaymentRestIntegrationTest.java"
Task: "Write gRPC client test in .../OrderGrpcIntegrationTest.java"
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL -- blocks all stories)
3. Complete Phase 3: User Story 1 (Synchronous Communication)
4. **STOP and VALIDATE**: REST, gRPC, GraphQL all work between services
5. Deploy to Kind cluster and verify

### Incremental Delivery

1. Setup + Foundational -> Foundation ready
2. US1 (Sync) -> Verify protocols independently -> **MVP!**
3. US2 (Async) -> Verify Kafka + RabbitMQ -> Deploy
4. US3 (Gateway) -> Verify routing, auth, rate limiting
5. US4 (Discovery) -> Verify K8s DNS, Eureka, Consul
6. US5 (Observability) -> Verify traces, metrics, logs
7. US6 (Circuit Breaker) -> Verify fault tolerance
8. US7 (Service Mesh) -> Verify mTLS, traffic management
9. US8 (E2E) -> Full business flow validation -> **Complete PoC!**
10. Polish -> Final cleanup and validation

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: US1 (Sync Communication)
   - Developer B: US2 (Async Communication)
   - Developer C: US4 (Service Discovery)
3. After US1 complete:
   - Developer A: US3 (API Gateway) + US6 (Circuit Breaker)
4. After US1 + US2 complete:
   - Developer B: US5 (Observability)
   - Developer C: US7 (Service Mesh)
5. Final: US8 (E2E) -- team collaboration

---

## Summary

| Phase | Stories | Task Count | Parallel Tasks |
|-------|---------|-----------|----------------|
| Phase 1: Setup | -- | 6 | 5 |
| Phase 2: Foundational | -- | 13 | 12 |
| Phase 3: US1 Sync | US1 | 34 | 25 |
| Phase 4: US2 Async | US2 | 38 | 26 |
| Phase 5: US3 Gateway | US3 | 7 | 3 |
| Phase 6: US4 Discovery | US4 | 6 | 5 |
| Phase 7: US5 Observability | US5 | 9 | 8 |
| Phase 8: US6 Circuit Breaker | US6 | 6 | 2 |
| Phase 9: US7 Service Mesh | US7 | 8 | 3 |
| Phase 10: US8 E2E | US8 | 7 | 4 |
| Phase 11: Polish | -- | 4 | 2 |
| **Total** | | **138** | **95** |

---

## Notes

- TDD is NON-NEGOTIABLE per constitution: all tests written and verified FAILING before implementation
- [P] tasks = different files, no dependencies -- safe to parallelize
- [Story] label maps each task to a specific user story for traceability
- ArchUnit tests enforce hexagonal architecture on every build
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
