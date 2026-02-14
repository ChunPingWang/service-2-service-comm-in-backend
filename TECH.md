# TECH: Service-to-Service Communication PoC

## Technical Design & Implementation Guide

**Version:** 1.0
**Date:** 2026-02-14
**Tech Stack:** Java 23 · Spring Boot 4 · Testcontainers · Kind (Kubernetes in Docker)

---

## 1. Architecture Overview

### 1.1 System Architecture

```
                          ┌─────────────────────────────────────────────────┐
                          │              Kind Kubernetes Cluster             │
                          │                                                 │
  ┌──────────┐           │  ┌────────────┐    ┌─────────────────────────┐  │
  │  Client   │──────────▶│  │ API Gateway │───▶│     Service Mesh        │  │
  │ (Test/UI) │           │  │ (APISIX)   │    │   (Istio / Linkerd)     │  │
  └──────────┘           │  └────────────┘    └─────────────────────────┘  │
                          │        │                      │                 │
                          │        ▼                      ▼                 │
                          │  ┌──────────┐ REST  ┌──────────┐              │
                          │  │  Order    │──────▶│ Product  │              │
                          │  │  Service  │ gRPC  │ Service  │              │
                          │  └──────────┘       └──────────┘              │
                          │        │                                       │
                          │        │ REST + Circuit Breaker                │
                          │        ▼                                       │
                          │  ┌──────────┐  Kafka   ┌──────────────┐       │
                          │  │ Payment  │────────▶│ Notification │       │
                          │  │ Service  │          │   Service    │       │
                          │  └──────────┘          └──────────────┘       │
                          │                              │                 │
                          │                     RabbitMQ │                 │
                          │                              ▼                 │
                          │                        ┌──────────┐           │
                          │                        │ Shipping │           │
                          │                        │ Service  │           │
                          │                        └──────────┘           │
                          │                                                │
                          │  ┌─────────────────────────────────────────┐   │
                          │  │          Observability Stack             │   │
                          │  │  OpenTelemetry → Jaeger (Tracing)       │   │
                          │  │  Prometheus → Grafana (Metrics)         │   │
                          │  │  Loki (Logs)                            │   │
                          │  └─────────────────────────────────────────┘   │
                          │                                                │
                          │  ┌─────────────────────────────────────────┐   │
                          │  │        Service Discovery                 │   │
                          │  │  K8s DNS / Eureka / Consul              │   │
                          │  └─────────────────────────────────────────┘   │
                          └─────────────────────────────────────────────────┘
```

### 1.2 Project Structure

```
microservices-comm-poc/
├── README.md
├── PRD.md
├── TECH.md
├── docker-compose.yml                    # Local dev (non-K8s)
├── kind-config.yaml                      # Kind cluster config
├── Makefile                              # Build/deploy automation
│
├── infrastructure/
│   ├── kind/
│   │   ├── kind-cluster.yaml
│   │   └── setup.sh
│   ├── k8s/
│   │   ├── namespaces.yaml
│   │   ├── api-gateway/
│   │   │   ├── apisix-deployment.yaml
│   │   │   └── apisix-routes.yaml
│   │   ├── service-mesh/
│   │   │   ├── istio-install.yaml
│   │   │   └── traffic-rules.yaml
│   │   ├── observability/
│   │   │   ├── otel-collector.yaml
│   │   │   ├── jaeger.yaml
│   │   │   ├── prometheus.yaml
│   │   │   ├── grafana.yaml
│   │   │   └── loki.yaml
│   │   ├── messaging/
│   │   │   ├── kafka.yaml
│   │   │   └── rabbitmq.yaml
│   │   └── service-discovery/
│   │       ├── eureka.yaml
│   │       └── consul.yaml
│   └── docker/
│       └── Dockerfile.service            # Multi-stage build template
│
├── proto/
│   └── product.proto                     # gRPC Protobuf definitions
│
├── services/
│   ├── order-service/
│   │   ├── pom.xml
│   │   ├── src/main/java/.../
│   │   │   ├── OrderServiceApplication.java
│   │   │   ├── adapter/
│   │   │   │   ├── in/
│   │   │   │   │   ├── rest/OrderController.java
│   │   │   │   │   └── graphql/OrderGraphQLController.java
│   │   │   │   └── out/
│   │   │   │       ├── rest/PaymentRestClient.java
│   │   │   │       ├── grpc/ProductGrpcClient.java
│   │   │   │       └── messaging/OrderEventPublisher.java
│   │   │   ├── application/
│   │   │   │   ├── port/in/CreateOrderUseCase.java
│   │   │   │   ├── port/out/ProductPort.java
│   │   │   │   └── service/OrderApplicationService.java
│   │   │   ├── domain/
│   │   │   │   ├── model/Order.java
│   │   │   │   └── event/OrderCreatedEvent.java
│   │   │   └── config/
│   │   │       ├── CircuitBreakerConfig.java
│   │   │       ├── GrpcClientConfig.java
│   │   │       └── ObservabilityConfig.java
│   │   └── src/test/java/.../
│   │       ├── integration/
│   │       │   ├── OrderRestIntegrationTest.java
│   │       │   ├── OrderGrpcIntegrationTest.java
│   │       │   └── OrderKafkaIntegrationTest.java
│   │       └── architecture/
│   │           └── HexagonalArchitectureTest.java
│   │
│   ├── product-service/
│   │   ├── pom.xml
│   │   └── src/main/java/.../
│   │       ├── adapter/in/grpc/ProductGrpcService.java
│   │       ├── adapter/in/rest/ProductController.java
│   │       ├── adapter/in/graphql/ProductGraphQLResolver.java
│   │       ├── application/service/ProductService.java
│   │       └── domain/model/Product.java
│   │
│   ├── payment-service/
│   │   ├── pom.xml
│   │   └── src/main/java/.../
│   │       ├── adapter/in/rest/PaymentController.java
│   │       ├── adapter/out/messaging/PaymentEventPublisher.java
│   │       └── config/
│   │           └── FaultSimulationConfig.java    # 模擬故障供 Circuit Breaker 測試
│   │
│   ├── notification-service/
│   │   ├── pom.xml
│   │   └── src/main/java/.../
│   │       ├── adapter/in/messaging/KafkaNotificationConsumer.java
│   │       ├── adapter/out/messaging/RabbitMQPublisher.java
│   │       └── application/service/NotificationService.java
│   │
│   └── shipping-service/
│       ├── pom.xml
│       └── src/main/java/.../
│           ├── adapter/in/messaging/RabbitMQShippingConsumer.java
│           └── application/service/ShippingService.java
│
├── e2e-tests/
│   ├── pom.xml
│   └── src/test/java/.../
│       ├── SyncCommunicationE2ETest.java
│       ├── AsyncCommunicationE2ETest.java
│       ├── CircuitBreakerE2ETest.java
│       ├── TracingE2ETest.java
│       └── GatewayE2ETest.java
│
└── docs/
    ├── architecture-decisions/
    │   ├── ADR-001-communication-protocol.md
    │   ├── ADR-002-api-gateway-selection.md
    │   └── ADR-003-service-mesh-selection.md
    └── diagrams/
        └── service-communication.puml
```

## 2. Technology Stack

### 2.1 Core Stack

| Category | Technology | Version | Purpose |
|----------|-----------|---------|---------|
| Language | Java | 23 | 主要開發語言 |
| Framework | Spring Boot | 4.x | 微服務框架 |
| Build | Maven | 3.9+ | 專案建置 |
| Container | Docker | 24+ | 容器化 |
| Orchestration | Kind | 0.24+ | 本地 K8s 叢集 |
| Testing | Testcontainers | 1.20+ | 整合測試容器 |
| Testing | JUnit 5 | 5.11+ | 單元/整合測試 |

### 2.2 Communication Stack

| Category | Technology | Purpose |
|----------|-----------|---------|
| REST | Spring WebFlux / MVC | HTTP/REST 通訊 |
| gRPC | grpc-spring-boot-starter | 高效能 RPC |
| GraphQL | Spring for GraphQL | 彈性查詢 |
| Kafka | Spring Kafka | 事件串流 |
| RabbitMQ | Spring AMQP | 訊息佇列 |

### 2.3 Infrastructure Stack

| Category | Technology | Purpose |
|----------|-----------|---------|
| API Gateway | Apache APISIX | 請求路由、限流、認證 |
| Service Mesh | Istio (or Linkerd) | 流量管理、mTLS |
| Service Discovery | Eureka / Consul / K8s DNS | 服務註冊發現 |
| Tracing | OpenTelemetry + Jaeger | 分散式追蹤 |
| Metrics | Prometheus + Grafana | 指標監控 |
| Logging | Loki + Promtail | 日誌聚合 |
| Circuit Breaker | Resilience4j | 容錯與降級 |

## 3. Detailed Technical Design

### 3.1 Synchronous Communication

#### 3.1.1 REST (Order → Payment)

```java
// PaymentRestClient.java — Adapter Out
@Component
public class PaymentRestClient implements PaymentPort {

    private final RestClient restClient;

    public PaymentRestClient(RestClient.Builder builder) {
        this.restClient = builder
            .baseUrl("http://payment-service:8080")
            .build();
    }

    @Override
    @CircuitBreaker(name = "payment", fallbackMethod = "paymentFallback")
    @Retry(name = "payment")
    public PaymentResponse processPayment(PaymentRequest request) {
        return restClient.post()
            .uri("/api/v1/payments")
            .body(request)
            .retrieve()
            .body(PaymentResponse.class);
    }

    private PaymentResponse paymentFallback(PaymentRequest request, Throwable t) {
        return PaymentResponse.pending(request.orderId(),
            "Payment service unavailable, queued for retry");
    }
}
```

#### 3.1.2 gRPC (Order → Product)

```protobuf
// proto/product.proto
syntax = "proto3";
package com.poc.product;

service ProductService {
  rpc GetProduct (GetProductRequest) returns (ProductResponse);
  rpc CheckInventory (CheckInventoryRequest) returns (InventoryResponse);
}

message GetProductRequest {
  string product_id = 1;
}

message ProductResponse {
  string product_id = 1;
  string name = 2;
  double price = 3;
  int32 stock = 4;
}

message CheckInventoryRequest {
  string product_id = 1;
  int32 quantity = 2;
}

message InventoryResponse {
  bool available = 1;
  int32 remaining_stock = 2;
}
```

```java
// ProductGrpcClient.java — Adapter Out
@Component
public class ProductGrpcClient implements ProductPort {

    private final ProductServiceGrpc.ProductServiceBlockingStub stub;

    public ProductGrpcClient(@GrpcClient("product-service")
                              ProductServiceGrpc.ProductServiceBlockingStub stub) {
        this.stub = stub;
    }

    @Override
    public Product getProduct(String productId) {
        var request = GetProductRequest.newBuilder()
            .setProductId(productId)
            .build();
        var response = stub.getProduct(request);
        return Product.fromGrpc(response);
    }
}
```

#### 3.1.3 GraphQL (Client → Product)

```graphql
# product-service/src/main/resources/graphql/schema.graphqls
type Query {
    product(id: ID!): Product
    products(category: String, limit: Int = 10): [Product!]!
}

type Product {
    id: ID!
    name: String!
    description: String
    price: Float!
    stock: Int!
    category: String!
}
```

```java
// ProductGraphQLController.java
@Controller
public class ProductGraphQLController {

    private final ProductQueryUseCase productQuery;

    @QueryMapping
    public Product product(@Argument String id) {
        return productQuery.findById(id);
    }

    @QueryMapping
    public List<Product> products(@Argument String category,
                                   @Argument int limit) {
        return productQuery.findByCategory(category, limit);
    }
}
```

### 3.2 Asynchronous Communication

#### 3.2.1 Kafka (Payment → Notification)

```java
// PaymentEventPublisher.java — Adapter Out
@Component
public class PaymentEventPublisher {

    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;
    private static final String TOPIC = "payment.completed";

    @Override
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        kafkaTemplate.send(TOPIC, event.orderId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish payment event: {}", event.orderId(), ex);
                } else {
                    log.info("Payment event published: {} at offset {}",
                        event.orderId(), result.getRecordMetadata().offset());
                }
            });
    }
}

// KafkaNotificationConsumer.java — Adapter In
@Component
public class KafkaNotificationConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
        topics = "payment.completed",
        groupId = "notification-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentCompleted(
            @Payload PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment ack) {
        try {
            notificationService.sendNotification(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment event: {}", key, e);
            // DLQ handling via error handler
            throw e;
        }
    }
}
```

#### 3.2.2 RabbitMQ (Notification → Shipping)

```java
// RabbitMQ Configuration
@Configuration
public class RabbitMQConfig {

    public static final String SHIPPING_EXCHANGE = "shipping.exchange";
    public static final String SHIPPING_QUEUE = "shipping.queue";
    public static final String SHIPPING_DLQ = "shipping.dlq";
    public static final String ROUTING_KEY = "shipping.create";

    @Bean
    public TopicExchange shippingExchange() {
        return new TopicExchange(SHIPPING_EXCHANGE);
    }

    @Bean
    public Queue shippingQueue() {
        return QueueBuilder.durable(SHIPPING_QUEUE)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", SHIPPING_DLQ)
            .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(SHIPPING_DLQ).build();
    }

    @Bean
    public Binding shippingBinding() {
        return BindingBuilder.bind(shippingQueue())
            .to(shippingExchange())
            .with(ROUTING_KEY);
    }
}
```

### 3.3 API Gateway (Apache APISIX)

```yaml
# infrastructure/k8s/api-gateway/apisix-routes.yaml
apiVersion: apisix.apache.org/v2
kind: ApisixRoute
metadata:
  name: poc-routes
  namespace: poc
spec:
  http:
    # Order Service
    - name: order-route
      match:
        paths: ["/api/v1/orders/*"]
      backends:
        - serviceName: order-service
          servicePort: 8080
      plugins:
        - name: jwt-auth
          enable: true
        - name: limit-req
          enable: true
          config:
            rate: 100
            burst: 50
            key_type: "var"
            key: "remote_addr"
            rejected_code: 429

    # Product Service (GraphQL)
    - name: product-graphql-route
      match:
        paths: ["/graphql"]
      backends:
        - serviceName: product-service
          servicePort: 8080
      plugins:
        - name: jwt-auth
          enable: true

    # Product Service (gRPC)
    - name: product-grpc-route
      match:
        paths: ["/com.poc.product.ProductService/*"]
      backends:
        - serviceName: product-service
          servicePort: 9090
          scheme: grpc
```

### 3.4 Circuit Breaker (Resilience4j)

```yaml
# order-service application.yml
resilience4j:
  circuitbreaker:
    instances:
      payment:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
        slidingWindowType: COUNT_BASED
        automaticTransitionFromOpenToHalfOpenEnabled: true
  retry:
    instances:
      payment:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - java.net.SocketTimeoutException
  timelimiter:
    instances:
      payment:
        timeoutDuration: 3s
```

### 3.5 Distributed Tracing (OpenTelemetry + Jaeger)

```yaml
# order-service application.yml
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces

logging:
  pattern:
    level: "%5p [${spring.application.name},%X{traceId},%X{spanId}]"
```

```java
// ObservabilityConfig.java
@Configuration
public class ObservabilityConfig {

    @Bean
    public ObservationFilter correlationIdFilter() {
        return context -> {
            // Propagate correlation ID across all observations
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                context.lowCardinalityKeyValue("correlation.id", correlationId);
            }
            return context;
        };
    }
}
```

### 3.6 Observability Stack — K8s Deployment

```yaml
# infrastructure/k8s/observability/otel-collector.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: otel-collector
  namespace: observability
spec:
  replicas: 1
  selector:
    matchLabels:
      app: otel-collector
  template:
    spec:
      containers:
        - name: otel-collector
          image: otel/opentelemetry-collector-contrib:latest
          ports:
            - containerPort: 4317   # gRPC OTLP
            - containerPort: 4318   # HTTP OTLP
            - containerPort: 8889   # Prometheus exporter
          volumeMounts:
            - name: config
              mountPath: /etc/otelcol-contrib/config.yaml
              subPath: config.yaml
      volumes:
        - name: config
          configMap:
            name: otel-collector-config
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: otel-collector-config
  namespace: observability
data:
  config.yaml: |
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: 0.0.0.0:4317
          http:
            endpoint: 0.0.0.0:4318
    processors:
      batch:
        timeout: 5s
    exporters:
      otlp/jaeger:
        endpoint: jaeger:4317
        tls:
          insecure: true
      prometheus:
        endpoint: 0.0.0.0:8889
    service:
      pipelines:
        traces:
          receivers: [otlp]
          processors: [batch]
          exporters: [otlp/jaeger]
        metrics:
          receivers: [otlp]
          processors: [batch]
          exporters: [prometheus]
```

## 4. Testing Strategy

### 4.1 Test Pyramid

```
              ┌─────────┐
              │  E2E    │  ← Kind 叢集 + 全服務部署
              │  Tests  │     (e2e-tests module)
             ┌┴─────────┴┐
             │ Integration │  ← Testcontainers
             │   Tests     │     (per-service)
            ┌┴─────────────┴┐
            │  Unit Tests    │  ← JUnit 5 + Mockito
            │                │     (per-service)
            └────────────────┘
```

### 4.2 Testcontainers Integration Tests

```java
// OrderKafkaIntegrationTest.java
@SpringBootTest
@Testcontainers
class OrderKafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    );

    @Container
    static GenericContainer<?> productService = new GenericContainer<>(
        DockerImageName.parse("poc/product-service:latest"))
        .withExposedPorts(8080, 9090)
        .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("product.service.url",
            () -> "http://localhost:" + productService.getMappedPort(8080));
    }

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Autowired
    private KafkaConsumer<String, String> testConsumer;

    @Test
    void shouldPublishOrderCreatedEventToKafka() {
        // Given
        var request = new CreateOrderRequest("product-1", 2, "customer-1");

        // When
        var order = createOrderUseCase.createOrder(request);

        // Then
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);

        // Verify Kafka event
        var records = KafkaTestUtils.getRecords(testConsumer, Duration.ofSeconds(10));
        assertThat(records).hasSize(1);

        var event = records.iterator().next();
        assertThat(event.key()).isEqualTo(order.id());
    }
}

// CircuitBreakerIntegrationTest.java
@SpringBootTest
@Testcontainers
class CircuitBreakerIntegrationTest {

    @Container
    static GenericContainer<?> paymentService = new GenericContainer<>(
        DockerImageName.parse("poc/payment-service:latest"))
        .withExposedPorts(8080)
        .withEnv("FAULT_SIMULATION_ENABLED", "true")
        .withEnv("FAULT_FAILURE_RATE", "100");  // 100% failure

    @Autowired
    private PaymentPort paymentPort;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void shouldOpenCircuitAfterFailureThreshold() {
        var cb = circuitBreakerRegistry.circuitBreaker("payment");

        // Trigger failures to open circuit
        for (int i = 0; i < 10; i++) {
            try {
                paymentPort.processPayment(
                    new PaymentRequest("order-" + i, BigDecimal.TEN));
            } catch (Exception ignored) {}
        }

        // Verify circuit is OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Verify fallback is returned
        var result = paymentPort.processPayment(
            new PaymentRequest("order-fallback", BigDecimal.TEN));
        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
    }
}

// GrpcIntegrationTest.java
@SpringBootTest
@Testcontainers
class GrpcIntegrationTest {

    @Container
    static GenericContainer<?> productService = new GenericContainer<>(
        DockerImageName.parse("poc/product-service:latest"))
        .withExposedPorts(9090)
        .waitingFor(Wait.forLogMessage(".*gRPC Server started.*", 1));

    @Test
    void shouldFetchProductViaGrpc() {
        var channel = ManagedChannelBuilder
            .forAddress("localhost", productService.getMappedPort(9090))
            .usePlaintext()
            .build();

        var stub = ProductServiceGrpc.newBlockingStub(channel);
        var response = stub.getProduct(
            GetProductRequest.newBuilder().setProductId("prod-001").build());

        assertThat(response.getName()).isNotBlank();
        assertThat(response.getPrice()).isGreaterThan(0);

        channel.shutdown();
    }
}
```

### 4.3 E2E Test (Kind Cluster)

```java
// SyncCommunicationE2ETest.java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SyncCommunicationE2ETest {

    static final String BASE_URL = System.getProperty(
        "gateway.url", "http://localhost:30080");

    private final RestClient client = RestClient.builder()
        .baseUrl(BASE_URL)
        .defaultHeader("Authorization", "Bearer " + getTestJwt())
        .build();

    @Test @Order(1)
    void restCommunication_shouldCreateOrder() {
        var response = client.post()
            .uri("/api/v1/orders")
            .body(Map.of("productId", "prod-001", "quantity", 2))
            .retrieve()
            .body(OrderResponse.class);

        assertThat(response.status()).isEqualTo("CREATED");
    }

    @Test @Order(2)
    void graphqlCommunication_shouldQueryProducts() {
        var query = """
            query { products(category: "electronics", limit: 5) {
                id name price stock
            }}""";

        var response = client.post()
            .uri("/graphql")
            .body(Map.of("query", query))
            .retrieve()
            .body(GraphQLResponse.class);

        assertThat(response.data().products()).isNotEmpty();
    }

    @Test @Order(3)
    void rateLimiting_shouldReturn429WhenExceeded() {
        // Rapid-fire requests to trigger rate limit
        int rejected = 0;
        for (int i = 0; i < 200; i++) {
            try {
                client.get().uri("/api/v1/orders").retrieve().toBodilessEntity();
            } catch (HttpClientErrorException.TooManyRequests e) {
                rejected++;
            }
        }
        assertThat(rejected).isGreaterThan(0);
    }
}
```

## 5. Infrastructure Setup

### 5.1 Kind Cluster Configuration

```yaml
# kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: microservices-poc
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - containerPort: 30080    # API Gateway
        hostPort: 30080
        protocol: TCP
      - containerPort: 30090    # Jaeger UI
        hostPort: 16686
        protocol: TCP
      - containerPort: 30091    # Grafana
        hostPort: 3000
        protocol: TCP
      - containerPort: 30092    # Prometheus
        hostPort: 9090
        protocol: TCP
  - role: worker
  - role: worker
```

### 5.2 Makefile

```makefile
.PHONY: all cluster-up cluster-down build deploy test clean

# ── Cluster ──────────────────────────────────
cluster-up:
	kind create cluster --config kind-config.yaml
	kubectl apply -f infrastructure/k8s/namespaces.yaml

cluster-down:
	kind delete cluster --name microservices-poc

# ── Build ────────────────────────────────────
build:
	./mvnw clean package -DskipTests
	$(MAKE) docker-build

docker-build:
	@for svc in order product payment notification shipping; do \
		docker build -t poc/$${svc}-service:latest \
			-f infrastructure/docker/Dockerfile.service \
			services/$${svc}-service; \
		kind load docker-image poc/$${svc}-service:latest \
			--name microservices-poc; \
	done

# ── Deploy ───────────────────────────────────
deploy-infra:
	kubectl apply -f infrastructure/k8s/observability/
	kubectl apply -f infrastructure/k8s/messaging/
	kubectl apply -f infrastructure/k8s/api-gateway/
	@echo "Waiting for infrastructure pods..."
	kubectl wait --for=condition=ready pod -l tier=infrastructure \
		--timeout=300s -n poc

deploy-services:
	kubectl apply -f infrastructure/k8s/services/
	@echo "Waiting for service pods..."
	kubectl wait --for=condition=ready pod -l tier=application \
		--timeout=300s -n poc

deploy: deploy-infra deploy-services

# ── Test ─────────────────────────────────────
test-unit:
	./mvnw test

test-integration:
	./mvnw verify -Pintegration-test

test-e2e:
	./mvnw verify -Pe2e-test -Dgateway.url=http://localhost:30080

test: test-unit test-integration

# ── All ──────────────────────────────────────
all: cluster-up build deploy test-e2e

clean: cluster-down
	./mvnw clean
```

### 5.3 Dockerfile (Multi-stage)

```dockerfile
# infrastructure/docker/Dockerfile.service
FROM eclipse-temurin:23-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -q

FROM eclipse-temurin:23-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/*.jar app.jar

ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0"

EXPOSE 8080 9090
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

## 6. Hexagonal Architecture Per Service

```
┌────────────────────────────────────────────────────────┐
│                    Application                         │
│  ┌──────────────┐   ┌────────────────────────────┐    │
│  │  Port In      │   │      Use Case / Service     │    │
│  │  (Interface)  │──▶│  (Business Logic)           │    │
│  └──────────────┘   └────────────────────────────┘    │
│                              │                         │
│                              ▼                         │
│                      ┌──────────────┐                  │
│                      │  Port Out     │                  │
│                      │  (Interface)  │                  │
│                      └──────────────┘                  │
└───────────┬──────────────────┬─────────────────────────┘
            │                  │
    ┌───────▼──────┐   ┌──────▼───────┐
    │ Adapter In    │   │ Adapter Out   │
    │ REST/gRPC/    │   │ REST Client/  │
    │ GraphQL/Kafka │   │ gRPC Client/  │
    │ Consumer      │   │ Kafka Producer│
    └──────────────┘   └──────────────┘
```

每個服務嚴格遵循 Hexagonal Architecture，domain 層零依賴外部框架。

## 7. Configuration Profiles

```yaml
# application.yml (common)
spring:
  application:
    name: ${SERVICE_NAME}
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

---
# application-local.yml (Docker Compose 開發)
spring:
  config:
    activate:
      on-profile: local
  kafka:
    bootstrap-servers: localhost:9092
  rabbitmq:
    host: localhost

---
# application-k8s.yml (Kind 叢集部署)
spring:
  config:
    activate:
      on-profile: k8s
  kafka:
    bootstrap-servers: kafka.poc.svc.cluster.local:9092
  rabbitmq:
    host: rabbitmq.poc.svc.cluster.local
```

## 8. Verification Checklist

### Phase-by-Phase Verification

| Phase | 驗證項目 | 驗證方式 | 預期結果 |
|-------|---------|---------|---------|
| 1 | Kind 叢集建立 | `kubectl get nodes` | 3 nodes Ready |
| 1 | Docker 映像建置 | `docker images \| grep poc` | 5 service images |
| 2 | REST 呼叫 | `curl /api/v1/orders` | 200 + JSON |
| 2 | gRPC 呼叫 | `grpcurl product:9090 GetProduct` | Protobuf response |
| 2 | GraphQL 呼叫 | `curl /graphql` + query | data.products 非空 |
| 3 | Kafka 事件 | Testcontainers test | Consumer 收到事件 |
| 3 | RabbitMQ 訊息 | Testcontainers test | Queue 消費成功 |
| 4 | API Gateway 路由 | `curl localhost:30080/api/v1/orders` | 正確轉發 |
| 4 | Rate Limiting | 快速連續 200 次請求 | 出現 429 |
| 4 | JWT Auth | 無 Token 請求 | 401 |
| 5 | Distributed Tracing | Jaeger UI | 完整 Trace 跨 3+ 服務 |
| 5 | Metrics | Grafana Dashboard | 服務指標可視化 |
| 5 | Aggregated Logs | Loki/Grafana | Correlation ID 一致 |
| 6 | Circuit Breaker | 故障模擬測試 | Circuit Open → Fallback |
| 6 | Retry + Backoff | 間歇故障模擬 | 自動重試成功 |

## 9. Dependencies & Maven BOM

```xml
<!-- Parent POM (root) -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.0</version>
</parent>

<properties>
    <java.version>23</java.version>
    <spring-cloud.version>2024.0.0</spring-cloud.version>
    <grpc-spring.version>3.2.0</grpc-spring.version>
    <testcontainers.version>1.20.4</testcontainers.version>
    <resilience4j.version>2.3.0</resilience4j.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-bom</artifactId>
            <version>${testcontainers.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 10. Operational Notes

### 10.1 Resource Requirements

| Component | CPU Request | Memory Request | Replicas |
|-----------|------------|----------------|----------|
| Each Service | 250m | 512Mi | 1 |
| Kafka | 500m | 1Gi | 1 |
| RabbitMQ | 250m | 512Mi | 1 |
| APISIX | 250m | 256Mi | 1 |
| Jaeger | 250m | 512Mi | 1 |
| Prometheus | 250m | 512Mi | 1 |
| Grafana | 100m | 256Mi | 1 |
| OTel Collector | 200m | 256Mi | 1 |
| **Total** | **~3.5 CPU** | **~6.5Gi** | - |

**Minimum Host:** 16GB RAM, 4 CPU cores (recommended 32GB/8 cores)

### 10.2 Quick Start

```bash
# 1. Clone & Build
git clone <repo-url> && cd microservices-comm-poc

# 2. One-command setup
make all

# 3. Access UIs
# Jaeger:     http://localhost:16686
# Grafana:    http://localhost:3000
# APISIX:     http://localhost:30080
# Prometheus: http://localhost:9090

# 4. Run specific tests
make test-unit
make test-integration
make test-e2e

# 5. Cleanup
make clean
```

---

*Document Owner: Application Architecture Team*
*Tech Lead Review Required Before Implementation*
