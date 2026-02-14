# PRD: Service-to-Service Communication PoC

## Product Requirements Document

**Version:** 1.0
**Date:** 2026-02-14
**Status:** Draft

---

## 1. Executive Summary

本專案旨在建立一個完整的 Proof of Concept（概念驗證），驗證微服務架構中 Service-to-Service 通訊的各種模式與基礎設施元件。透過 Java 23 與 Spring Boot 4 技術棧，在本地 Kubernetes（Kind）環境中，端到端驗證同步/非同步通訊、服務治理、可觀測性、與容錯機制的整合可行性。

## 2. Business Context

### 2.1 Problem Statement

企業在進行微服務架構轉型時，面臨以下挑戰：

- 服務間通訊模式選擇缺乏實證依據（REST vs gRPC vs GraphQL vs Messaging）
- 跨服務呼叫的可靠性、可觀測性、安全性難以在開發初期驗證
- 團隊對 Service Mesh、API Gateway、Circuit Breaker 等基礎設施元件的整合經驗不足
- 缺少可重複部署的本地驗證環境，導致架構決策延遲

### 2.2 Business Objectives

| 目標 | 衡量標準 | 預期成果 |
|------|---------|---------|
| 驗證通訊模式可行性 | 所有 8 大場景通過 E2E 測試 | 產出技術選型建議報告 |
| 降低架構決策風險 | PoC 完成後可量化各模式效能差異 | 有數據支撐的架構決策 |
| 加速團隊技術掌握 | 團隊成員可獨立操作 PoC 環境 | 技術知識內化 |
| 建立可重複的驗證平台 | 一鍵部署、一鍵測試 | 未來架構驗證可重用 |

### 2.3 Target Users

- **Enterprise Architects**: 評估技術選型與架構決策
- **Application Architects**: 驗證設計模式與整合方案
- **Development Teams**: 學習與實踐微服務通訊模式
- **DevOps Engineers**: 驗證部署與運維方案

## 3. Scope

### 3.1 In Scope — 8 大驗證場景

#### 場景 1：Synchronous Communication（同步通訊）

**業務價值**：驗證服務間即時請求-回應模式的適用場景與效能差異。

| 子場景 | 說明 |
|--------|------|
| HTTP/REST | 標準 RESTful API 呼叫，JSON 格式 |
| gRPC | 高效能二進位協定，適用於內部服務間通訊 |
| GraphQL | 彈性查詢，適用於 BFF (Backend for Frontend) 場景 |

**驗證重點**：延遲比較、吞吐量差異、Payload 大小影響、錯誤處理方式。

#### 場景 2：Asynchronous Communication（非同步通訊）

**業務價值**：驗證事件驅動與訊息佇列模式在解耦、削峰填谷場景的表現。

| 子場景 | 說明 |
|--------|------|
| Kafka | 高吞吐量事件串流，適用於 Event Sourcing |
| RabbitMQ | 傳統訊息佇列，支援多種交換模式 |
| SQS (模擬) | 雲端佇列服務模擬，驗證雲原生整合模式 |

**驗證重點**：訊息可靠性、順序保證、Consumer Group 機制、Dead Letter Queue。

#### 場景 3：API Gateway

**業務價值**：驗證統一入口對請求路由、限流、認證、轉換的管理能力。

| 功能 | 說明 |
|------|------|
| Request Routing | 基於路徑/Header 的動態路由 |
| Rate Limiting | 流量控制與限流策略 |
| Authentication | JWT/OAuth2 認證整合 |
| Transformations | 請求/回應格式轉換 |

#### 場景 4：Service Registry & Discovery（服務註冊與發現）

**業務價值**：驗證動態服務發現機制，消除硬編碼端點依賴。

| 方案 | 說明 |
|------|------|
| Eureka | Spring Cloud 原生服務發現 |
| Consul | HashiCorp 多功能服務網格 |
| Kubernetes DNS | K8s 原生服務發現機制 |

#### 場景 5：Service Mesh

**業務價值**：驗證基礎設施層面的流量管理、可觀測性、安全性。

| 功能 | 說明 |
|------|------|
| Sidecar Proxy | Envoy/Istio Sidecar 注入 |
| Traffic Management | 金絲雀部署、流量鏡像、故障注入 |
| Observability | 自動指標收集與追蹤 |
| mTLS Security | 服務間雙向 TLS 加密 |

#### 場景 6：Distributed Tracing（分散式追蹤）

**業務價值**：驗證跨服務呼叫鏈的端到端可視化與效能瓶頸定位。

| 元件 | 說明 |
|------|------|
| OpenTelemetry | 標準化遙測資料收集 |
| Jaeger | 分散式追蹤後端與 UI |

#### 場景 7：Circuit Breaker（斷路器）

**業務價值**：驗證服務降級與容錯機制，防止級聯失敗。

| 模式 | 說明 |
|------|------|
| Retries | 自動重試策略（指數退避） |
| Backoff | 退避演算法（Exponential Backoff with Jitter） |
| Fallback | 降級回退邏輯 |

#### 場景 8：Logging & Monitoring（日誌與監控）

**業務價值**：驗證統一日誌聚合與指標告警平台。

| 功能 | 說明 |
|------|------|
| Aggregated Logs | ELK/Loki 集中式日誌 |
| Metrics & Alerts | Prometheus + Grafana 指標與告警 |

### 3.2 Out of Scope

- 生產環境部署與調優
- 多雲/混合雲部署方案
- 商業授權產品整合（如 Kong Enterprise、Datadog）
- 效能壓力測試（非 PoC 目標，但預留擴展介面）
- 資料持久化與資料庫選型驗證

## 4. Business Domain — PoC 業務模型

為使 PoC 貼近真實業務場景，採用簡化的**電子商務訂單處理**領域模型：

```
[Customer] → [Order Service] → [Product Service]    (同步 REST/gRPC)
                    ↓
            [Payment Service]                        (同步 REST)
                    ↓
            [Notification Service]                   (非同步 Kafka/RabbitMQ)
                    ↓
            [Shipping Service]                       (非同步 Event)
```

### 4.1 業務流程

1. **查詢商品**：Customer 透過 API Gateway 查詢商品（GraphQL）
2. **建立訂單**：Order Service 呼叫 Product Service 確認庫存（gRPC）
3. **處理付款**：Order Service 呼叫 Payment Service（REST + Circuit Breaker）
4. **發送通知**：Payment 成功後發布事件至 Kafka
5. **安排出貨**：Notification Service 消費事件並通知 Shipping Service（RabbitMQ）

## 5. Success Criteria

### 5.1 Functional Criteria

| ID | 驗證項目 | 通過條件 |
|----|---------|---------|
| F-01 | REST 服務間呼叫 | 200 OK 回應，JSON 正確序列化 |
| F-02 | gRPC 服務間呼叫 | Protobuf 正確序列化/反序列化 |
| F-03 | GraphQL 查詢 | 彈性欄位查詢正確回傳 |
| F-04 | Kafka 事件發布/消費 | 訊息可靠送達、Consumer Offset 正確 |
| F-05 | RabbitMQ 訊息傳遞 | 佇列消費正確、ACK 機制驗證 |
| F-06 | API Gateway 路由 | 所有路由正確轉發 |
| F-07 | Rate Limiting | 超過閾值回傳 429 |
| F-08 | JWT Authentication | 無效 Token 回傳 401 |
| F-09 | Service Discovery | 動態註冊與發現 |
| F-10 | Distributed Tracing | Trace 跨 3+ 服務完整串連 |
| F-11 | Circuit Breaker | 失敗率達閾值後 Circuit Open |
| F-12 | Fallback | Circuit Open 時 Fallback 回應 |
| F-13 | Aggregated Logs | 跨服務日誌 Correlation ID 一致 |
| F-14 | Metrics | Prometheus 收集到所有服務指標 |

### 5.2 Non-Functional Criteria

| ID | 項目 | 條件 |
|----|------|------|
| NF-01 | 環境建置 | Kind 叢集 10 分鐘內就緒 |
| NF-02 | 全量部署 | 所有服務 15 分鐘內部署完成 |
| NF-03 | 自動化測試 | Testcontainers 整合測試全數通過 |
| NF-04 | 文件完整性 | README 包含完整操作指引 |

## 6. Assumptions & Constraints

### 6.1 Assumptions

- 開發團隊具備 Java 與 Spring Boot 基礎知識
- 開發機器至少 16GB RAM、4 CPU cores
- Docker Desktop 或 Colima 已安裝並可用
- 網路環境可存取 Maven Central、Docker Hub

### 6.2 Constraints

- 所有元件必須可在本地 Kind 叢集執行
- 不依賴任何雲端服務（SQS 使用 LocalStack 模擬）
- 技術棧限定 Java 23 + Spring Boot 4
- 測試框架限定 Testcontainers + JUnit 5

## 7. Risks & Mitigations

| 風險 | 影響 | 可能性 | 緩解措施 |
|------|------|-------|---------|
| Spring Boot 4 尚在早期階段 | 部分 Starter 不相容 | 高 | 準備 Spring Boot 3.4 降級方案 |
| Kind 資源不足 | 多服務無法同時啟動 | 中 | 分階段驗證，非同時部署所有場景 |
| Istio 在 Kind 上的限制 | Service Mesh 場景受限 | 中 | 備選 Linkerd（更輕量） |
| Java 23 Preview Features | 不穩定性 | 低 | 僅使用穩定 API |

## 8. Timeline

| Phase | Duration | 交付物 |
|-------|----------|-------|
| Phase 1: Foundation | 1 week | 專案骨架、Kind 叢集、CI Pipeline |
| Phase 2: Sync Communication | 1 week | REST/gRPC/GraphQL 服務與測試 |
| Phase 3: Async Communication | 1 week | Kafka/RabbitMQ 整合與測試 |
| Phase 4: Infrastructure | 1 week | API Gateway、Service Discovery |
| Phase 5: Observability | 1 week | Tracing、Logging、Monitoring |
| Phase 6: Resilience | 0.5 week | Circuit Breaker、Fallback |
| Phase 7: Integration & Docs | 0.5 week | E2E 測試、文件完善 |

**Total: 6 weeks**

## 9. Glossary

| 術語 | 定義 |
|------|------|
| PoC | Proof of Concept，概念驗證 |
| Kind | Kubernetes IN Docker，本地 K8s 叢集工具 |
| BFF | Backend for Frontend，前端專用後端 |
| mTLS | Mutual TLS，雙向 TLS 認證 |
| DLQ | Dead Letter Queue，死信佇列 |
| Circuit Breaker | 斷路器模式，防止級聯失敗 |
| Sidecar | 邊車模式，與主服務共存的輔助容器 |
| Correlation ID | 關聯 ID，用於跨服務日誌追蹤 |

---

*Document Owner: Enterprise Architecture Team*
*Review Cycle: Bi-weekly*
