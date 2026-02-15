#!/usr/bin/env bash
#
# Kind cluster setup script for S2S Communication PoC.
#
# Creates a Kind cluster, builds and loads Docker images, deploys
# infrastructure (Kafka, RabbitMQ, observability) and all 5 services,
# waits for pods to become ready, and sets up port-forwarding.
#
# Usage:
#   ./infrastructure/kind/setup.sh          # Full setup
#   ./infrastructure/kind/setup.sh teardown  # Tear down the cluster
#
set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────

CLUSTER_NAME="s2s-comm-poc"
NAMESPACE="poc"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
KIND_CONFIG="${SCRIPT_DIR}/kind-cluster.yaml"
K8S_DIR="${PROJECT_ROOT}/infrastructure/k8s"
DOCKER_DIR="${PROJECT_ROOT}/infrastructure/docker"

SERVICES=(
    "order-service"
    "product-service"
    "payment-service"
    "notification-service"
    "shipping-service"
)

SERVICE_PORTS=(
    "order-service:8081"
    "product-service:8082"
    "payment-service:8083"
    "notification-service:8084"
    "shipping-service:8085"
)

# Pod readiness timeout in seconds
READINESS_TIMEOUT=180

# ── Helper Functions ──────────────────────────────────────────────────────────

log_info() {
    echo "[INFO] $*"
}

log_error() {
    echo "[ERROR] $*" >&2
}

log_step() {
    echo ""
    echo "========================================"
    echo "  $*"
    echo "========================================"
    echo ""
}

check_prerequisites() {
    local missing=()

    for cmd in kind kubectl docker; do
        if ! command -v "${cmd}" &>/dev/null; then
            missing+=("${cmd}")
        fi
    done

    if [[ ${#missing[@]} -gt 0 ]]; then
        log_error "Missing required tools: ${missing[*]}"
        log_error "Please install them before running this script."
        exit 1
    fi

    # Check if Docker daemon is running
    if ! docker info &>/dev/null; then
        log_error "Docker daemon is not running. Please start Docker."
        exit 1
    fi
}

# ── Teardown ──────────────────────────────────────────────────────────────────

teardown() {
    log_step "Tearing down Kind cluster: ${CLUSTER_NAME}"

    # Kill any running port-forward processes
    log_info "Stopping port-forward processes..."
    pkill -f "kubectl port-forward.*-n ${NAMESPACE}" 2>/dev/null || true

    # Delete the Kind cluster
    if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
        log_info "Deleting Kind cluster: ${CLUSTER_NAME}"
        kind delete cluster --name "${CLUSTER_NAME}"
        log_info "Cluster deleted."
    else
        log_info "Cluster '${CLUSTER_NAME}' does not exist. Nothing to delete."
    fi

    log_info "Teardown complete."
}

# ── Cluster Creation ──────────────────────────────────────────────────────────

create_cluster() {
    log_step "Creating Kind cluster: ${CLUSTER_NAME}"

    # Check if cluster already exists
    if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
        log_info "Cluster '${CLUSTER_NAME}' already exists. Skipping creation."
        return 0
    fi

    kind create cluster --config "${KIND_CONFIG}"
    log_info "Cluster created successfully."

    # Verify cluster is accessible
    kubectl cluster-info --context "kind-${CLUSTER_NAME}"
}

# ── Namespace Setup ───────────────────────────────────────────────────────────

setup_namespaces() {
    log_step "Setting up namespaces"
    kubectl apply -f "${K8S_DIR}/namespaces.yaml"
    log_info "Namespaces created."
}

# ── Docker Image Build & Load ─────────────────────────────────────────────────

build_and_load_images() {
    log_step "Building Maven modules"
    cd "${PROJECT_ROOT}"

    # Build all modules (skip tests for speed)
    ./mvnw package -DskipTests -B -q
    log_info "Maven build complete."

    log_step "Building Docker images"
    for svc in "${SERVICES[@]}"; do
        log_info "Building image for ${svc}..."
        docker build \
            -t "${NAMESPACE}/${svc}:latest" \
            --build-arg SERVICE_MODULE="${svc}" \
            -f "${DOCKER_DIR}/Dockerfile.service" \
            "${PROJECT_ROOT}"
    done
    log_info "All Docker images built."

    log_step "Loading images into Kind cluster"
    for svc in "${SERVICES[@]}"; do
        log_info "Loading ${NAMESPACE}/${svc}:latest..."
        kind load docker-image "${NAMESPACE}/${svc}:latest" --name "${CLUSTER_NAME}"
    done
    log_info "All images loaded into cluster."
}

# ── Infrastructure Deployment ─────────────────────────────────────────────────

deploy_infrastructure() {
    log_step "Deploying infrastructure (Kafka, RabbitMQ, Observability)"

    # Deploy messaging infrastructure
    log_info "Deploying messaging (Kafka, RabbitMQ)..."
    kubectl apply -f "${K8S_DIR}/messaging/" -n "${NAMESPACE}"

    # Deploy observability stack
    log_info "Deploying observability (Jaeger, Prometheus, Grafana, OTel Collector, Loki)..."
    kubectl apply -f "${K8S_DIR}/observability/" -n "${NAMESPACE}"

    # Deploy API gateway
    log_info "Deploying API gateway (APISIX)..."
    kubectl apply -f "${K8S_DIR}/api-gateway/" -n "${NAMESPACE}"

    # Deploy service discovery
    log_info "Deploying service discovery (Consul, Eureka)..."
    kubectl apply -f "${K8S_DIR}/service-discovery/" -n "${NAMESPACE}"

    log_info "Infrastructure manifests applied."
}

# ── Service Deployment ────────────────────────────────────────────────────────

deploy_services() {
    log_step "Deploying application services"
    kubectl apply -f "${K8S_DIR}/services/" -n "${NAMESPACE}"
    log_info "Service manifests applied."
}

# ── Wait for Readiness ────────────────────────────────────────────────────────

wait_for_pods() {
    log_step "Waiting for all pods to be ready (timeout: ${READINESS_TIMEOUT}s)"

    # Wait for infrastructure pods
    log_info "Waiting for infrastructure pods..."
    kubectl wait --for=condition=ready pod \
        --all \
        -n "${NAMESPACE}" \
        --timeout="${READINESS_TIMEOUT}s" 2>/dev/null || {
        log_error "Some pods did not become ready within ${READINESS_TIMEOUT}s."
        log_error "Current pod status:"
        kubectl get pods -n "${NAMESPACE}" -o wide
        exit 1
    }

    log_info "All pods are ready."
    kubectl get pods -n "${NAMESPACE}" -o wide
}

# ── Port Forwarding ──────────────────────────────────────────────────────────

setup_port_forwarding() {
    log_step "Setting up port forwarding"

    # Kill any existing port-forward processes
    pkill -f "kubectl port-forward.*-n ${NAMESPACE}" 2>/dev/null || true
    sleep 1

    # Port-forward each service
    for mapping in "${SERVICE_PORTS[@]}"; do
        local svc="${mapping%%:*}"
        local port="${mapping##*:}"
        log_info "Port-forwarding ${svc} -> localhost:${port}"
        kubectl port-forward "svc/${svc}" "${port}:${port}" -n "${NAMESPACE}" &
    done

    # Port-forward Jaeger UI (16686)
    log_info "Port-forwarding Jaeger UI -> localhost:16686"
    kubectl port-forward svc/jaeger 16686:16686 -n "${NAMESPACE}" &

    # Port-forward Prometheus (9090)
    log_info "Port-forwarding Prometheus -> localhost:9090"
    kubectl port-forward svc/prometheus 9090:9090 -n "${NAMESPACE}" &

    # Port-forward Grafana (3000)
    log_info "Port-forwarding Grafana -> localhost:3000"
    kubectl port-forward svc/grafana 3000:3000 -n "${NAMESPACE}" &

    # Wait briefly for port-forwards to establish
    sleep 3

    log_info "Port forwarding established."
    log_info ""
    log_info "Services available at:"
    for mapping in "${SERVICE_PORTS[@]}"; do
        local svc="${mapping%%:*}"
        local port="${mapping##*:}"
        log_info "  ${svc}: http://localhost:${port}"
    done
    log_info "  Jaeger UI: http://localhost:16686"
    log_info "  Prometheus: http://localhost:9090"
    log_info "  Grafana: http://localhost:3000"
}

# ── Main ──────────────────────────────────────────────────────────────────────

main() {
    # Handle teardown subcommand
    if [[ "${1:-}" == "teardown" ]]; then
        teardown
        exit 0
    fi

    log_step "S2S Communication PoC - Kind Cluster Setup"

    check_prerequisites
    create_cluster
    setup_namespaces
    build_and_load_images
    deploy_infrastructure

    # Give infrastructure time to start before deploying services
    log_info "Waiting 15s for infrastructure to initialize..."
    sleep 15

    deploy_services
    wait_for_pods
    setup_port_forwarding

    log_step "Setup Complete!"
    log_info "The Kind cluster '${CLUSTER_NAME}' is ready."
    log_info "All services are deployed in namespace '${NAMESPACE}'."
    log_info ""
    log_info "To run E2E tests:"
    log_info "  ./mvnw test -pl e2e-tests -B"
    log_info ""
    log_info "To tear down:"
    log_info "  ./infrastructure/kind/setup.sh teardown"
}

main "$@"
