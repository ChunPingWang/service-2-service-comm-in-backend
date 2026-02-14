# Phase 0 Research: Service-to-Service Communication PoC

**Feature Branch**: `001-s2s-comm-poc`
**Date**: 2026-02-14
**Status**: Complete
**Input**: [TECH.md](../../TECH.md), [PRD.md](../../PRD.md), [spec.md](spec.md), [plan.md](plan.md)

## Purpose

This document consolidates the technology research and selection decisions for
the Service-to-Service Communication PoC. Each section records what was chosen,
why it was chosen, and what alternatives were evaluated. The full technology
stack is defined in TECH.md; this document provides the rationale behind those
decisions.

---

## 1. Synchronous Communication

### 1.1 REST (HTTP/JSON)

**Decision**: Spring WebFlux/MVC with `RestClient` (Spring Boot 4 native)

**Rationale**: REST over HTTP/JSON is the most widely adopted service-to-service
protocol. Spring Boot 4 ships `RestClient` as a modern, fluent, synchronous HTTP
client that replaces the deprecated `RestTemplate`. It integrates natively with
Micrometer Observation for distributed tracing, and Resilience4j annotations
(`@CircuitBreaker`, `@Retry`) can be applied directly to client methods. REST is
used for the Order-to-Payment path where the request-response semantic is
straightforward, payloads are moderate-sized, and human readability of JSON aids
debugging. WebFlux is available for reactive use cases, but the PoC primarily
uses MVC-style blocking calls with `RestClient` to keep the programming model
simple and focus on communication pattern validation rather than reactive
paradigms.

**Alternatives considered**:
- **Apache HttpClient 5**: Mature and feature-rich, but lacks Spring's
  auto-configured observation integration. Would require manual wiring of
  tracing propagation and circuit breaker decoration. Rejected to avoid
  boilerplate.
- **WebClient (reactive)**: Ships with Spring WebFlux and supports non-blocking
  I/O. Suitable for high-concurrency scenarios, but adds reactive complexity
  (Mono/Flux) that is unnecessary for this PoC's functional validation goals.
  Kept as an option if the PoC later expands to reactive benchmarking.
- **Feign (Spring Cloud OpenFeign)**: Declarative HTTP client with interface-based
  contracts. Convenient for simple CRUD, but the Spring Cloud OpenFeign project
  is in maintenance mode and lags behind Spring Boot 4 compatibility. Rejected
  in favor of the first-class `RestClient`.

### 1.2 gRPC

**Decision**: `grpc-spring-boot-starter` 3.2.0 (from `net.devh`)

**Rationale**: gRPC provides high-performance binary serialization (Protobuf),
HTTP/2 multiplexing, and strong contract-first development via `.proto` files.
It is used for the Order-to-Product path where low-latency inventory checks
benefit from compact payloads and streaming potential. The `grpc-spring-boot-starter`
(also known as `grpc-ecosystem/grpc-spring`) integrates gRPC servers and clients
into the Spring Boot lifecycle with auto-configuration, health checks, and
Micrometer metrics. Version 3.2.0 is the latest stable release with Spring Boot
3.x/4.x support. The `@GrpcClient` annotation and `@GrpcService` annotation
align with Spring's convention-over-configuration philosophy.

**Alternatives considered**:
- **Plain grpc-java**: The official gRPC library from Google. Fully functional
  but requires manual server bootstrap, health service registration, and
  Spring integration. Rejected because it duplicates work that the starter
  automates.
- **Armeria**: A high-performance async RPC framework that supports gRPC,
  Thrift, and REST. Feature-rich but introduces a parallel server runtime that
  competes with Spring's embedded server. Rejected to avoid dual-server
  complexity in a Spring-centric project.
- **Connect-RPC**: A newer protocol that layers gRPC semantics over standard
  HTTP/1.1 and HTTP/2 with JSON or Protobuf. Promising for browser clients but
  the Java ecosystem support is still maturing. Rejected due to limited Spring
  integration and smaller community.

### 1.3 GraphQL

**Decision**: Spring for GraphQL (shipped with Spring Boot 4)

**Rationale**: GraphQL provides flexible, client-driven querying where the
consumer specifies exactly which fields to retrieve. It is used for the
Client-to-Product path to demonstrate a Backend-for-Frontend (BFF) query
pattern. Spring for GraphQL is the official Spring project (replacing the
community-driven graphql-spring-boot-starter) and integrates directly with
Spring MVC/WebFlux controllers via `@QueryMapping`, `@MutationMapping`, and
`@SchemaMapping`. It supports schema-first development with `.graphqls` files,
DataLoader for N+1 prevention, and Micrometer observation out of the box.

**Alternatives considered**:
- **Netflix DGS Framework**: A production-grade GraphQL framework built on
  graphql-java. Offers code generation, federation support, and a rich testing
  library. However, it adds a dependency on Netflix's release cycle and
  duplicates functionality now available natively in Spring for GraphQL.
  Rejected to minimize dependency surface and align with Spring's official
  roadmap.
- **graphql-java (raw)**: The underlying engine used by both Spring for GraphQL
  and DGS. Using it directly provides maximum control but requires manual
  wiring of HTTP transport, data fetchers, and instrumentation. Rejected
  because Spring for GraphQL wraps it with sensible defaults.
- **Hasura / Apollo Federation (external)**: Server-side GraphQL gateways.
  Out of scope for a Java-native PoC focused on in-process communication
  patterns.

---

## 2. Asynchronous Communication

### 2.1 Apache Kafka

**Decision**: Spring Kafka (with Spring Boot 4 auto-configuration)

**Rationale**: Kafka is chosen for the Payment-to-Notification event path where
high-throughput, durable, ordered event streaming is required. Spring Kafka
provides `KafkaTemplate` for production and `@KafkaListener` for consumption
with consumer group management, offset tracking, and manual acknowledgment.
Key capabilities validated by the PoC include: partitioned event ordering by
order ID (used as the message key), consumer group rebalancing, and Dead Letter
Topic (DLT) handling via `DefaultErrorHandler` with `DeadLetterPublishingRecoverer`.
Kafka's log-based architecture ensures events are retained for replay, which is
critical for event sourcing and audit trail scenarios.

**Alternatives considered**:
- **Apache Pulsar**: A newer distributed messaging system with built-in
  multi-tenancy, geo-replication, and unified streaming/queuing. Technically
  capable, but the Spring ecosystem integration (spring-pulsar) is less mature
  than Spring Kafka. The Pulsar community is smaller, and operational tooling
  (monitoring, debugging) is less established. Rejected for PoC pragmatism.
- **Amazon SQS (via LocalStack)**: Mentioned in the PRD as a potential option.
  Deferred because Kafka and RabbitMQ already cover the core async patterns
  (event streaming and traditional queuing), and adding SQS simulation via
  LocalStack introduces additional infrastructure complexity without validating
  a fundamentally different communication model.
- **Redis Streams**: Lightweight streaming with Redis. Suitable for simpler
  use cases but lacks Kafka's durability guarantees, partition semantics, and
  consumer group sophistication. Rejected for not representing a
  production-grade event streaming platform.
- **NATS JetStream**: High-performance messaging with persistence. Growing
  ecosystem but limited Spring Boot integration and smaller Java community
  compared to Kafka. Rejected for ecosystem maturity reasons.

### 2.2 RabbitMQ

**Decision**: Spring AMQP (with Spring Boot 4 auto-configuration)

**Rationale**: RabbitMQ is chosen for the Notification-to-Shipping path to
demonstrate traditional message queuing with exchange/queue binding, routing
keys, and point-to-point delivery. This contrasts with Kafka's pub/sub
streaming model and validates a different async pattern. Spring AMQP provides
`RabbitTemplate` for publishing and `@RabbitListener` for consumption.
The PoC configures a Topic Exchange (`shipping.exchange`), a durable queue
(`shipping.queue`), and a Dead Letter Queue (`shipping.dlq`) to demonstrate
message acknowledgment, rejection, and DLQ routing. RabbitMQ's AMQP 0-9-1
protocol is mature, well-documented, and operationally straightforward.

**Alternatives considered**:
- **ActiveMQ Artemis**: A JMS-compliant broker from Apache. Fully functional
  but JMS is a heavier abstraction than AMQP for this PoC's needs. RabbitMQ's
  management UI, plugin ecosystem, and Kubernetes operator are more
  developer-friendly. Rejected.
- **Using Kafka for both paths**: Kafka can handle point-to-point patterns via
  single-partition topics. However, the PoC explicitly aims to compare two
  different messaging paradigms (event streaming vs. message queuing), so using
  a single technology would defeat the validation objective. Rejected by design.

---

## 3. API Gateway

### 3.1 Apache APISIX

**Decision**: Apache APISIX (deployed in Kind cluster)

**Rationale**: APISIX is a high-performance, cloud-native API gateway built on
Nginx and LuaJIT. It was selected for three key reasons: (1) native Kubernetes
integration via the APISIX Ingress Controller, which allows route definitions
as CRDs (`ApisixRoute`); (2) built-in plugins for rate limiting (`limit-req`),
JWT authentication (`jwt-auth`), and request transformation without custom code;
(3) native gRPC proxying support, which is critical for the Product Service's
gRPC endpoint. APISIX's plugin architecture is extensible, and its etcd-based
configuration store supports dynamic route updates without restarts.

**Alternatives considered**:
- **Spring Cloud Gateway**: The Spring ecosystem's native gateway, built on
  Project Reactor and WebFlux. Strong integration with Spring Cloud service
  discovery and circuit breaker. However, it is a Java application that
  consumes significant memory (~512Mi) compared to APISIX's Nginx-based
  footprint (~256Mi). Its gRPC proxying support is limited and requires
  additional configuration. For a PoC running in a resource-constrained Kind
  cluster, APISIX's lower resource footprint and richer out-of-box plugin
  library were preferred. Rejected for resource efficiency and gRPC support.
- **Kong Gateway (OSS)**: An established API gateway also built on Nginx/
  OpenResty. Functionally comparable to APISIX with a large plugin ecosystem.
  However, Kong's OSS version has a more restrictive plugin model (many
  enterprise features require Kong Enterprise), and its Kubernetes Ingress
  Controller configuration is more verbose than APISIX's CRD-based approach.
  APISIX's fully open-source plugin library and Apache Software Foundation
  governance were preferred. Rejected for licensing model and CRD ergonomics.
- **Envoy (standalone)**: A high-performance L4/L7 proxy often used as a
  service mesh data plane. Capable as a gateway but requires significant xDS
  configuration or a control plane (like Istio). Since the PoC already uses
  Istio for service mesh, using Envoy directly as the gateway would blur the
  boundary between gateway and mesh concerns. Rejected for separation of
  concerns.
- **Traefik**: A cloud-native reverse proxy with automatic service discovery
  and Let's Encrypt integration. Good for simple routing but its middleware
  plugin system is less mature than APISIX's for advanced use cases (custom
  rate limiting algorithms, request body transformation). Rejected for plugin
  depth.

---

## 4. Service Discovery

### 4.1 Multi-Strategy Approach

**Decision**: Validate all three mechanisms -- Kubernetes DNS (primary),
Eureka (Spring Cloud native), and Consul (HashiCorp multi-purpose)

**Rationale**: The PoC explicitly aims to compare service discovery approaches
rather than commit to one. Each mechanism represents a different operational
model:

- **Kubernetes DNS**: Zero-application-dependency discovery via K8s Service
  objects. Services reference each other by DNS name
  (`payment-service.poc.svc.cluster.local`). This is the simplest approach
  in a Kubernetes environment and is the primary mechanism used during Kind
  deployment. No additional infrastructure components are required.

- **Eureka (Spring Cloud Netflix)**: Application-level service registry with
  heartbeat-based health checking. Services register themselves on startup and
  discover peers via the Eureka client. Represents the Spring Cloud-native
  approach and is widely adopted in existing Spring microservice deployments.
  Included to validate migration paths from legacy Spring Cloud architectures.

- **Consul (HashiCorp)**: A multi-purpose service mesh and discovery tool with
  health checking, KV store, and DNS interface. Represents the infrastructure-
  level discovery approach that is framework-agnostic. Included to validate
  polyglot service discovery scenarios.

**Alternatives considered**:
- **Apache ZooKeeper**: A distributed coordination service historically used for
  service discovery (notably by older Kafka and Hadoop deployments). Being
  phased out in favor of newer alternatives; Kafka itself is removing the
  ZooKeeper dependency. Rejected as a legacy approach.
- **etcd (direct)**: The distributed KV store used by Kubernetes itself and by
  APISIX. Capable of service discovery but requires custom client
  implementation. Rejected because K8s DNS already leverages etcd indirectly.
- **Nacos (Alibaba)**: A service discovery and configuration platform popular in
  the Chinese Java ecosystem. Fully featured but less adopted in Western
  enterprise contexts and adds another infrastructure component. Rejected to
  keep the discovery comparison focused on the three most representative models.

---

## 5. Service Mesh

### 5.1 Istio (Primary) and Linkerd (Backup)

**Decision**: Istio as the primary service mesh; Linkerd as a lightweight
backup option

**Rationale**: Istio is the most widely adopted service mesh with comprehensive
traffic management (canary deployments, traffic mirroring, fault injection),
security (mTLS auto-rotation), and observability (automatic metrics, traces).
Its Envoy-based sidecar proxy provides fine-grained control over service-to-
service traffic without application code changes. Istio is selected as primary
because it covers all mesh scenarios in the PRD (FR-016 through FR-018) and has
the largest community and ecosystem.

Linkerd is retained as a backup because Istio's resource footprint is
significant (~2 CPU, ~2Gi memory for the control plane) and may strain the Kind
cluster's limited resources. Linkerd's Rust-based micro-proxy consumes
substantially less memory (~50Mi per sidecar vs. ~128Mi for Envoy) and has a
simpler operational model. If Istio proves too heavy for the local Kind
environment, Linkerd provides the core mesh capabilities (mTLS, traffic
splitting, observability) with lower overhead.

**Alternatives considered**:
- **Consul Connect**: HashiCorp's service mesh built on Consul. Since Consul is
  already included for service discovery, using Consul Connect would unify
  discovery and mesh. However, its traffic management features (canary, fault
  injection) are less mature than Istio's VirtualService/DestinationRule model.
  Rejected for feature depth in traffic management.
- **AWS App Mesh / GCP Traffic Director**: Cloud-provider-specific mesh
  solutions. Out of scope because the PoC must run entirely in a local Kind
  cluster without cloud dependencies.
- **Cilium Service Mesh**: An eBPF-based mesh that avoids sidecar proxies
  entirely. Promising for performance but requires Linux kernel 5.10+ and
  specific CNI configuration that may not work cleanly in Kind's Docker-based
  networking. Rejected for Kind compatibility concerns.
- **No mesh (skip)**: The communication patterns (REST, gRPC, Kafka, RabbitMQ)
  work without a mesh. However, the PRD explicitly includes service mesh as
  scenario 5 with mTLS, traffic management, and fault injection requirements.
  Skipping is not an option.

---

## 6. Distributed Tracing

### 6.1 OpenTelemetry + Jaeger

**Decision**: OpenTelemetry SDK and Collector for instrumentation and collection;
Jaeger for trace storage and visualization

**Rationale**: OpenTelemetry (OTel) is the CNCF standard for telemetry data
(traces, metrics, logs). Spring Boot 4 integrates with OTel via Micrometer
Observation, which automatically instruments Spring MVC, WebFlux, RestClient,
Kafka, and RabbitMQ. The OTel Collector acts as a vendor-neutral pipeline that
receives OTLP data and exports to multiple backends. Jaeger is chosen as the
tracing backend because it is open-source, provides a rich UI for trace
visualization, supports OTLP ingestion natively, and runs well in Kubernetes.
The combination provides end-to-end trace propagation across all communication
paths (REST, gRPC, Kafka, RabbitMQ) with a single trace ID connecting all 5
services.

**Alternatives considered**:
- **Zipkin**: An earlier distributed tracing system from Twitter. Simpler to
  deploy (single JAR) but has a less feature-rich UI and lacks OTel-native
  OTLP ingestion (requires a separate adapter). Spring Boot has legacy Zipkin
  support, but the direction is toward OTel/OTLP. Rejected in favor of the
  more modern Jaeger.
- **Tempo (Grafana)**: A cost-effective trace backend optimized for object
  storage. Strong Grafana integration and TraceQL query language. A valid
  choice, but Jaeger's standalone deployment is simpler for a PoC (no object
  storage dependency) and its dedicated trace UI is more feature-complete for
  trace exploration. Rejected for deployment simplicity, though it would be
  a strong production choice alongside the existing Grafana stack.
- **Datadog / New Relic / Dynatrace**: Commercial APM platforms with
  comprehensive tracing, metrics, and log correlation. Out of scope because
  the PoC must not depend on commercial cloud services.
- **Spring Cloud Sleuth**: The legacy Spring tracing library. Deprecated in
  favor of Micrometer Tracing with OTel bridge. Rejected as it is no longer
  actively developed.

---

## 7. Metrics and Monitoring

### 7.1 Prometheus + Grafana

**Decision**: Prometheus for metrics scraping and storage; Grafana for
dashboards and visualization

**Rationale**: Prometheus is the de facto standard for Kubernetes-native metrics
collection. Spring Boot Actuator exposes a `/actuator/prometheus` endpoint via
Micrometer, which Prometheus scrapes at configurable intervals. The OTel
Collector also exports metrics in Prometheus format via its Prometheus exporter
(port 8889). Grafana provides dashboards for visualizing service metrics
(request rate, latency percentiles, error rate, JVM metrics) and supports
Prometheus as a first-class data source. Both tools are CNCF projects, widely
adopted, and have extensive community dashboard libraries. The combination
covers FR-011 (metrics collection) and the monitoring aspects of scenario 8.

**Alternatives considered**:
- **InfluxDB + Chronograf**: A time-series database with its own visualization
  tool. Less Kubernetes-native than Prometheus and requires a push-based metric
  model. The PoC already uses Prometheus-compatible endpoints via Spring Boot
  Actuator and OTel Collector. Rejected for ecosystem alignment.
- **VictoriaMetrics**: A Prometheus-compatible TSDB with better storage
  efficiency and long-term retention. A strong production alternative but
  unnecessary for a short-lived PoC where Prometheus's 15-day default retention
  is sufficient. Rejected for PoC simplicity.
- **Grafana Mimir**: Grafana's scalable Prometheus-compatible backend. Adds
  operational complexity (multi-component deployment) that is unnecessary for
  a single-node PoC. Rejected.

---

## 8. Logging

### 8.1 Loki + Promtail

**Decision**: Grafana Loki for log aggregation; Promtail for log shipping

**Rationale**: Loki is chosen to complement the existing Grafana deployment
(used for metrics dashboards). Loki's label-based indexing (rather than
full-text indexing) makes it lightweight and efficient for a resource-
constrained Kind cluster. Promtail runs as a DaemonSet on each Kind node,
tailing container log files and shipping them to Loki with Kubernetes metadata
labels (pod name, namespace, service name). Within Grafana, operators can
correlate logs with traces by clicking from a Jaeger trace span to the
corresponding Loki log entries using the correlation ID embedded in the log
pattern (`[${spring.application.name},%X{traceId},%X{spanId}]`). This
fulfills FR-012 (aggregated logs with correlation IDs).

**Alternatives considered**:
- **ELK Stack (Elasticsearch + Logstash + Kibana)**: The most established
  centralized logging solution. Powerful full-text search and analytics, but
  Elasticsearch is memory-intensive (minimum ~1Gi per node) and would strain
  the Kind cluster's resource budget. Kibana adds another UI alongside Grafana.
  Rejected for resource footprint and UI fragmentation.
- **Fluentd / Fluent Bit**: Alternative log shippers that could replace
  Promtail. Fluent Bit is especially lightweight. However, Promtail is
  purpose-built for Loki and requires zero configuration for Kubernetes
  metadata enrichment. Using Fluentd would require additional output plugin
  configuration. Rejected for configuration simplicity.
- **OpenTelemetry Collector (for logs)**: The OTel Collector can also collect
  and forward logs. This would unify the telemetry pipeline, but OTel log
  support is still stabilizing (logs are the newest OTel signal). Promtail is
  more mature for Kubernetes log collection today. Rejected for maturity,
  though this is the likely future direction.

---

## 9. Circuit Breaker

### 9.1 Resilience4j 2.3.0

**Decision**: Resilience4j 2.3.0 with Spring Boot 4 integration

**Rationale**: Resilience4j is a lightweight, modular fault tolerance library
designed for Java functional programming. It provides circuit breaker, retry,
rate limiter, bulkhead, and time limiter modules that compose via decorators.
Version 2.3.0 is the latest stable release with Spring Boot 3.x/4.x
auto-configuration support. Key capabilities used in the PoC:

- **Circuit Breaker**: Sliding window (count-based, 10 calls), 50% failure
  threshold, 10s open state duration, automatic half-open transition.
- **Retry**: 3 attempts with exponential backoff (1s base, 2x multiplier).
- **Time Limiter**: 3s timeout for synchronous calls.
- **Fallback**: Method-level fallback via `@CircuitBreaker(fallbackMethod=...)`.

These are applied to the Order-to-Payment REST path where the Payment Service
has a fault simulation mode (`FaultSimulationConfig`) to validate circuit
breaker state transitions (FR-013, FR-014, FR-015).

**Alternatives considered**:
- **Hystrix (Netflix)**: The original circuit breaker library for Java
  microservices. Entered maintenance mode in 2018 and is no longer actively
  developed. No Spring Boot 4 support. Rejected as deprecated.
- **Spring Retry**: Provides retry logic with `@Retryable` annotation. Covers
  the retry pattern but lacks circuit breaker, bulkhead, and rate limiter
  modules. Would need to be combined with another library for full resilience.
  Rejected for incomplete coverage.
- **Sentinel (Alibaba)**: A flow control and circuit breaking library with a
  dashboard. Feature-rich but heavier than Resilience4j and more oriented
  toward Alibaba's cloud ecosystem. Rejected for ecosystem fit.
- **Service Mesh Circuit Breaking (Istio)**: Istio provides outlier detection
  and circuit breaking at the infrastructure level. Valuable but operates on
  connection-level metrics rather than application-level semantics (e.g.,
  business error codes). The PoC uses both: Resilience4j for application-aware
  fault tolerance and Istio for infrastructure-level traffic management.
  Not rejected but complementary.

---

## 10. Architecture Pattern

### 10.1 Hexagonal Architecture with DDD

**Decision**: Hexagonal Architecture (Ports & Adapters) per service, with
Domain-Driven Design bounded contexts

**Rationale**: Hexagonal Architecture isolates business logic from
infrastructure concerns by defining ports (interfaces) that adapters implement.
This is especially valuable in a PoC comparing multiple communication protocols
because:

- The same use case (`CreateOrderUseCase`) can be invoked via REST, GraphQL, or
  gRPC adapters without modifying business logic.
- Outbound adapters (`PaymentRestClient`, `ProductGrpcClient`,
  `OrderEventPublisher`) can be swapped or extended without touching the
  application service layer.
- Domain models (`Order`, `Product`, `Payment`) have zero framework
  dependencies, making them portable and testable with plain JUnit.

DDD bounded contexts map naturally to the 5 microservices: Order, Product,
Payment, Notification, and Shipping. Each service owns its domain model and
communicates via well-defined contracts (REST APIs, Protobuf messages, Kafka
events, RabbitMQ messages). Domain events (`OrderCreatedEvent`,
`PaymentCompletedEvent`) model state transitions across context boundaries.

The project enforces architecture rules via ArchUnit tests
(`HexagonalArchitectureTest`) that verify: (1) domain layer has no imports from
adapter or config packages; (2) application layer depends only on domain and
port interfaces; (3) adapters depend on the application layer only through
ports.

**Alternatives considered**:
- **Layered Architecture (Controller-Service-Repository)**: The traditional
  Spring pattern. Simpler to scaffold but creates tight coupling between layers
  (services depend directly on repositories, controllers depend directly on
  services). Swapping a REST adapter for a gRPC adapter would require
  restructuring. Rejected for inflexibility in a multi-protocol PoC.
- **Clean Architecture (Uncle Bob)**: Conceptually similar to Hexagonal with
  concentric rings (Entities, Use Cases, Interface Adapters, Frameworks).
  The distinction is largely terminological. Hexagonal's port/adapter vocabulary
  is more concrete and maps directly to Spring component stereotypes. Preferred
  for clarity.
- **CQRS + Event Sourcing**: A more complex pattern that separates read and
  write models. Powerful for complex domains but overkill for a PoC focused on
  communication patterns rather than data modeling. Rejected for scope control.
- **Microkernel / Plugin Architecture**: Useful for extensible platforms but
  not a natural fit for microservices that communicate over network boundaries.
  Rejected as inapplicable.

---

## 11. Testing Strategy

### 11.1 JUnit 5 + Testcontainers 1.20.4 + ArchUnit

**Decision**: Three-tier testing pyramid with JUnit 5 (unit), Testcontainers
1.20.4 (integration), ArchUnit (architecture enforcement), and Kind-based E2E
tests

**Rationale**:

- **JUnit 5**: The standard Java testing framework. Provides `@Nested` test
  classes for organizing tests by scenario, `@ParameterizedTest` for data-driven
  protocol comparisons, and `@TestMethodOrder` for E2E test sequencing.

- **Testcontainers 1.20.4**: Enables integration tests with real infrastructure
  (Kafka, RabbitMQ, gRPC services) in Docker containers managed by the test
  lifecycle. `@DynamicPropertySource` injects container endpoints into Spring
  context. Version 1.20.4 includes the latest Kafka, RabbitMQ, and generic
  container modules. This eliminates the need for mocking message brokers or
  gRPC servers, providing high-fidelity integration validation.

- **ArchUnit**: Enforces architectural constraints as executable tests. The
  `HexagonalArchitectureTest` verifies that domain classes have no framework
  imports, application services depend only on ports, and adapters do not
  bypass ports. This prevents architectural drift as the PoC grows.

- **Kind E2E Tests**: End-to-end tests run against the deployed Kind cluster
  with all services, gateway, and observability stack. These validate the
  complete business flow (SC-012) and cross-cutting concerns (tracing,
  rate limiting, JWT auth) in a realistic Kubernetes environment.

**Alternatives considered**:
- **WireMock / MockServer**: HTTP mocking libraries for simulating external
  services. Useful for unit-level isolation but do not validate actual protocol
  behavior (serialization, error codes, timeouts). Rejected as the primary
  integration testing approach; Testcontainers provides higher fidelity. May
  be used selectively for edge case simulation.
- **Cucumber / JBehave (BDD frameworks)**: Given-When-Then scenario execution
  frameworks. The spec already defines acceptance scenarios in Given-When-Then
  format, but implementing them with Cucumber adds a Gherkin parsing layer that
  is unnecessary when JUnit 5 tests can express the same scenarios directly.
  Rejected for indirection overhead.
- **Spock Framework (Groovy)**: A Groovy-based testing framework with built-in
  mocking and specification-style syntax. Requires Groovy compilation and is
  less commonly used with modern Spring Boot. Rejected for language consistency.
- **Spring Cloud Contract**: Consumer-driven contract testing framework.
  Valuable for multi-team microservice development but adds complexity (contract
  DSL, stub generation) that is unnecessary for a single-team PoC where all
  services are developed together. Deferred for potential future use.
- **Karate DSL**: An API testing framework with BDD-style syntax for REST and
  GraphQL. Readable but introduces a separate test runtime. Rejected for
  toolchain consistency with JUnit.

---

## 12. Container and Orchestration

### 12.1 Docker 24+ Multi-Stage Builds

**Decision**: Multi-stage Docker builds with Eclipse Temurin 23 images

**Rationale**: Multi-stage builds separate the build environment (JDK + Maven)
from the runtime environment (JRE only), producing minimal container images.
The build stage uses `eclipse-temurin:23-jdk-alpine` with Maven cache mounting
(`--mount=type=cache,target=/root/.m2`) for fast rebuilds. The runtime stage
uses `eclipse-temurin:23-jre-alpine` with a non-root user (`app`), ZGC garbage
collector (`-XX:+UseZGC`), and 75% RAM allocation (`-XX:MaxRAMPercentage=75.0`).
Each service exposes ports 8080 (HTTP) and 9090 (gRPC). Docker 24+ is required
for BuildKit cache mount support.

**Alternatives considered**:
- **Spring Boot Buildpacks (CNB)**: Spring Boot's built-in `spring-boot:build-image`
  Maven goal produces OCI images without a Dockerfile. Convenient but less
  transparent and harder to customize (JVM flags, non-root user, multi-port
  exposure). Rejected for control and transparency.
- **GraalVM Native Image**: Compiles Java to native executables with instant
  startup and low memory. Attractive for microservices but many Spring Boot 4
  starters (especially gRPC, Kafka, Resilience4j) have incomplete GraalVM
  metadata. Build times are also significantly longer (~5min per service).
  Rejected for compatibility risk in a multi-dependency PoC.
- **Jib (Google)**: A containerization tool that builds images without Docker
  daemon access. Efficient for CI/CD but requires Docker daemon for Kind image
  loading (`kind load docker-image`), negating the main advantage. Rejected.
- **Distroless images**: Google's minimal container images without a shell or
  package manager. More secure but harder to debug in a PoC environment where
  `kubectl exec` troubleshooting is frequent. Rejected for debuggability.

### 12.2 Kind 0.24+

**Decision**: Kind (Kubernetes IN Docker) 0.24+ for local Kubernetes deployment

**Rationale**: Kind runs a multi-node Kubernetes cluster inside Docker
containers. It is purpose-built for CI and local development, starts in under
60 seconds, and supports `extraPortMappings` for exposing NodePorts to the host
(API Gateway on 30080, Jaeger on 16686, Grafana on 3000, Prometheus on 9090).
The PoC cluster configuration uses 1 control-plane node and 2 worker nodes.
Kind supports `kind load docker-image` for loading locally built images without
a registry, which simplifies the build-deploy cycle. Version 0.24+ supports
Kubernetes 1.31+ and is compatible with Istio's minimum version requirements.

**Alternatives considered**:
- **Minikube**: The most well-known local Kubernetes tool. Supports multiple
  drivers (Docker, VirtualBox, HyperKit). However, it defaults to a single-node
  cluster and uses more resources than Kind for multi-node configurations. Kind's
  Docker-based approach is lighter and faster for CI pipelines. Rejected for
  resource efficiency.
- **k3d (k3s in Docker)**: Runs Rancher's k3s (lightweight Kubernetes) in
  Docker containers. Faster startup and lower resource usage than Kind, but
  k3s removes some Kubernetes features (e.g., cloud controller manager, some
  storage provisioners) that may affect Istio compatibility. Rejected for
  full Kubernetes API compatibility.
- **Docker Desktop Kubernetes**: Built into Docker Desktop with a single
  checkbox. Simplest setup but single-node only, no custom configuration, and
  tied to Docker Desktop's release cycle. Rejected for inflexibility.
- **MicroK8s / K0s**: Lightweight Kubernetes distributions. Designed for edge
  and IoT rather than local development. Less community tooling for development
  workflows. Rejected.

---

## Summary Matrix

| Category | Decision | Key Factor |
|----------|----------|------------|
| REST | Spring WebFlux/MVC + RestClient | Native Spring Boot 4, observation integration |
| gRPC | grpc-spring-boot-starter 3.2.0 | Spring lifecycle integration, auto-configuration |
| GraphQL | Spring for GraphQL | Official Spring project, schema-first |
| Kafka | Spring Kafka | Event streaming, consumer groups, DLT support |
| RabbitMQ | Spring AMQP | Traditional queuing, exchange/queue binding, DLQ |
| API Gateway | Apache APISIX | Low resource footprint, gRPC proxy, CRD-based routing |
| Service Discovery | K8s DNS + Eureka + Consul | Multi-strategy comparison per PRD |
| Service Mesh | Istio (primary), Linkerd (backup) | Comprehensive traffic management, mTLS |
| Distributed Tracing | OpenTelemetry + Jaeger | CNCF standard, Spring Boot 4 native integration |
| Metrics | Prometheus + Grafana | K8s-native scraping, rich dashboards |
| Logging | Loki + Promtail | Lightweight, Grafana-native, label-based indexing |
| Circuit Breaker | Resilience4j 2.3.0 | Modular, lightweight, Spring Boot auto-config |
| Architecture | Hexagonal + DDD | Protocol-agnostic business logic, ArchUnit-enforced |
| Testing | JUnit 5 + Testcontainers 1.20.4 + ArchUnit | Real infrastructure, architecture enforcement |
| Container | Docker 24+ multi-stage | Minimal images, build cache, non-root runtime |
| Orchestration | Kind 0.24+ | Fast local K8s, multi-node, CI-friendly |

---

*Phase 0 complete. Proceed to Phase 1 (data model, contracts, quickstart).*
