.PHONY: all build test test-unit test-integration test-e2e \
       cluster-up cluster-down deploy-infra deploy-services clean

CLUSTER_NAME := s2s-comm-poc
NAMESPACE := poc
SERVICES := order-service product-service payment-service notification-service shipping-service

# === Full Lifecycle ===

all: cluster-up build deploy-infra deploy-services test-e2e
	@echo "✅ Full lifecycle complete."

# === Build ===

build:
	@echo "🔨 Building all modules..."
	./mvnw package -DskipTests -B -q
	@echo "🐳 Building Docker images..."
	@for svc in $(SERVICES); do \
		docker build -t $(NAMESPACE)/$$svc:latest \
			--build-arg SERVICE_MODULE=$$svc \
			-f infrastructure/docker/Dockerfile.service . ; \
	done
	@echo "📦 Loading images into Kind..."
	@for svc in $(SERVICES); do \
		kind load docker-image $(NAMESPACE)/$$svc:latest --name $(CLUSTER_NAME) ; \
	done

# === Test ===

test: test-unit test-integration

test-unit:
	@echo "🧪 Running unit tests..."
	./mvnw test -pl services/order-service,services/product-service,services/payment-service,services/notification-service,services/shipping-service -B

test-integration:
	@echo "🧪 Running integration tests..."
	./mvnw verify -pl services/order-service,services/product-service,services/payment-service,services/notification-service,services/shipping-service -B -Pintegration

test-e2e:
	@echo "🧪 Running E2E tests..."
	./mvnw test -pl e2e-tests -B

# === Kind Cluster ===

cluster-up:
	@echo "🚀 Creating Kind cluster..."
	kind create cluster --config infrastructure/kind/kind-cluster.yaml
	kubectl apply -f infrastructure/k8s/namespaces.yaml
	@echo "✅ Cluster ready."

cluster-down:
	@echo "🗑️  Deleting Kind cluster..."
	kind delete cluster --name $(CLUSTER_NAME)

# === Deploy ===

deploy-infra:
	@echo "📡 Deploying infrastructure..."
	kubectl apply -f infrastructure/k8s/messaging/ -n $(NAMESPACE)
	kubectl apply -f infrastructure/k8s/observability/ -n $(NAMESPACE)
	kubectl apply -f infrastructure/k8s/api-gateway/ -n $(NAMESPACE)
	kubectl apply -f infrastructure/k8s/service-discovery/ -n $(NAMESPACE)
	@echo "⏳ Waiting for infrastructure pods..."
	kubectl wait --for=condition=ready pod -l tier=infrastructure -n $(NAMESPACE) --timeout=120s
	@echo "✅ Infrastructure ready."

deploy-services:
	@echo "🚢 Deploying services..."
	kubectl apply -f infrastructure/k8s/services/ -n $(NAMESPACE)
	@echo "⏳ Waiting for service pods..."
	kubectl wait --for=condition=ready pod -l tier=application -n $(NAMESPACE) --timeout=120s
	@echo "✅ Services ready."

# === Cleanup ===

clean: cluster-down
	@echo "🧹 Cleaning build artifacts..."
	./mvnw clean -B -q
	@echo "✅ Clean complete."
