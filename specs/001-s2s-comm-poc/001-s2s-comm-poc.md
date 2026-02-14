# Specification Analysis Report: Service-to-Service Communication PoC

**Feature**: `001-s2s-comm-poc`
**Date**: 2026-02-14
**Artifacts Analyzed**: spec.md, plan.md, tasks.md, constitution.md, data-model.md, contracts/
**Status**: Pre-implementation analysis

---

## Findings

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| F1 | Inconsistency | HIGH | plan.md:172-179, kafka-events.md:11-13 | Payment Service structure in plan.md lists only `adapter/in/rest/` but kafka-events.md contract declares Payment Service as consumer of `order.created` Kafka topic, requiring an `adapter/in/messaging/` directory | Add `adapter/in/messaging/` with `KafkaOrderEventConsumer.java` and `OrderEventMapper.java` to Payment Service in plan.md |
| F2 | Underspecification | HIGH | data-model.md:1169-1174, spec.md:US8 scenario 2 | Order status transitions to SHIPPED (data-model.md Section 11 Step 5) but no event or callback from Shipping Service to Order Service is defined in any contract. The `order reaches SHIPPED status` acceptance criterion (US8) has no triggering mechanism | Define a `ShipmentArrangedEvent` (Kafka or callback) from Shipping to Order, or remove Order SHIPPED status from E2E flow scope |
| F3 | Inconsistency | HIGH | kafka-events.md:31-36, rest-api.yaml:/api/v1/payments, spec.md:US1-S2/US2-S1 | Payment processing has dual triggers: (1) Order calls Payment via REST synchronously (US1, rest-api.yaml), (2) Payment Service consumes `order.created` Kafka topic (kafka-events.md). Both paths target the same service for the same action, risking double-processing | Clarify in spec: REST path is the sync payment trigger; Kafka `order.created` consumer group should be a different service or explicitly documented as an alternative async path for pattern comparison only |
| F4 | Coverage Gap | HIGH | tasks.md (US2), kafka-events.md:100-119, rabbitmq-messages.md:83-108 | FR-020 (DLQ handling for Kafka and RabbitMQ) has no explicit implementation task. DLQ topics (`order.created.dlq`, `payment.completed.dlq`) and RabbitMQ DLQ (`shipping.dlq`) need configuration but only appear implicitly in infrastructure tasks T076-T080 | Add explicit task: "Configure Kafka DLQ topics and Spring `DefaultErrorHandler` with `DeadLetterPublishingRecoverer`" and "Configure RabbitMQ DLQ with x-dead-letter-exchange arguments" |
| F5 | Inconsistency | MEDIUM | plan.md:86-88, contracts/graphql-schema.graphqls | Plan.md lists `OrderGraphQLController.java` and `OrderGraphQLMapper.java` in Order Service `adapter/in/graphql/`, but the GraphQL schema defines only Product queries. No tasks create these files (correct), but plan.md project structure is misleading | Remove `adapter/in/graphql/` from Order Service in plan.md project structure |
| F6 | Underspecification | MEDIUM | plan.md:182-183, tasks.md:T065 | Payment Service has only `ProcessPaymentUseCase` as inbound port. If Payment consumes `order.created` Kafka events (per kafka-events.md), it needs a separate inbound port (e.g., `HandleOrderCreatedUseCase`) per ISP (constitution Principle III) | Add `HandleOrderCreatedUseCase` to `payment-service/application/port/in/` in plan.md and tasks |
| F7 | Coverage Gap | MEDIUM | spec.md:SC-011, tasks.md:Phase 7 | SC-011 requires "aggregated logs from a multi-service request share the same correlation ID and are queryable from the centralized logging platform." Phase 7 has tracing test (T095) and metrics test (T096) but no log correlation verification test | Add task: "Write log correlation verification test in e2e-tests/.../LogCorrelationTest.java" to Phase 7 |
| F8 | Coverage Gap | MEDIUM | spec.md:SC-013, tasks.md:Phase 11 | SC-013 requires "entire local environment deployable within 15 minutes from clean state." T127 (quickstart validation) does not explicitly measure or validate timing | Add timing assertion to T127 or create dedicated deployment timing validation task |
| F9 | Inconsistency | LOW | tasks.md:T073 | T073 names the Payment Kafka consumer "KafkaOrderConsumer" which follows the topic-name convention, but Notification Service uses "KafkaNotificationConsumer" (service-name convention). Naming convention is inconsistent across services | Standardize: use source-topic convention (e.g., `OrderCreatedEventConsumer`) or service-name convention (e.g., `PaymentKafkaConsumer`) consistently |
| F10 | Inconsistency | LOW | plan.md:65, contracts/graphql-schema.graphqls:1-6 | GraphQL schema file in contracts/ is named `graphql-schema.graphqls` but the target location in tasks (T044) is `services/product-service/src/main/resources/graphql/schema.graphqls` — different filename | Ensure runtime schema filename matches Spring for GraphQL convention (`schema.graphqls` in `resources/graphql/`) — T044 is correct; contracts/ filename is just for reference |

---

## Coverage Summary

### Functional Requirements (FR-001 to FR-020)

| Requirement | Description | Has Task? | Task IDs | Notes |
|-------------|-------------|-----------|----------|-------|
| FR-001 | REST communication | Yes | T036, T039, T046, T048, T050 | |
| FR-002 | gRPC communication | Yes | T037, T040, T041, T047 | |
| FR-003 | GraphQL queries | Yes | T038, T042, T044 | |
| FR-004 | Kafka async events | Yes | T068, T069, T072, T073 | |
| FR-005 | RabbitMQ messaging | Yes | T070, T071, T074, T075, T080 | |
| FR-006 | API Gateway routing | Yes | T082, T085, T086 | |
| FR-007 | Rate limiting (429) | Yes | T083, T088 | |
| FR-008 | JWT authentication (401) | Yes | T084, T087 | |
| FR-009 | Service discovery | Yes | T089-T094 | |
| FR-010 | Trace propagation | Yes | T095, T102, T103 | |
| FR-011 | Metrics collection | Yes | T096, T099, T100 | |
| FR-012 | Structured logs with correlation | Yes | T101, T102 | Missing test (F7) |
| FR-013 | Circuit breaker | Yes | T104-T109 | |
| FR-014 | Retry with backoff | Yes | T104, T107, T109 | |
| FR-015 | Fallback responses | Yes | T104, T107 | |
| FR-016 | Service mesh sidecar | Yes | T110, T113 | |
| FR-017 | mTLS | Yes | T111, T115 | |
| FR-018 | Traffic management | Yes | T112, T116, T117 | |
| FR-019 | E2E business flow | Yes | T118-T122 | |
| FR-020 | DLQ handling | Partial | (implicit in T076-T080) | No explicit task (F4) |

### Success Criteria (SC-001 to SC-014)

| Criterion | Description | Verified By | Notes |
|-----------|-------------|-------------|-------|
| SC-001 | 3 sync protocols work | T036-T040 | |
| SC-002 | Kafka events within 5s | T068-T069 | |
| SC-003 | RabbitMQ delivery within 5s | T070-T071 | |
| SC-004 | Gateway zero misroute in 1000 | T082 | |
| SC-005 | Rate limiting <5% false positive | T083 | |
| SC-006 | JWT rejects 100% invalid | T084 | |
| SC-007 | Discovery within 30s/deregister 60s | T089-T091 | |
| SC-008 | Traces span 3+ services | T095 | |
| SC-009 | Circuit breaker fallback <50ms | T104-T105 | |
| SC-010 | DLQ preserves full content | (implicit) | Gap: no explicit DLQ content verification test |
| SC-011 | Correlated logs queryable | -- | Gap: no test task (F7) |
| SC-012 | E2E trace across 5 services | T122 | |
| SC-013 | Deployable within 15 minutes | T127 | Gap: no timing assertion (F8) |
| SC-014 | All tests pass in CI | T125-T128 | |

---

## Constitution Alignment

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Hexagonal Architecture | PASS | All services follow convention. ArchUnit tests (T013-T017) enforce structure. |
| II | Layer Isolation & Dependency Inversion | PASS | Ports in application layer. Mappers at adapter boundaries. One concern: F6 (missing inbound port for Payment Kafka path). |
| III | SOLID Principles | PASS | Granular port interfaces. F6 highlights a potential ISP gap if Payment's single `ProcessPaymentUseCase` handles both REST and Kafka paths. |
| IV | Domain-Driven Design | PASS | 5 bounded contexts. Domain events. Value objects as records. No cross-context sharing. |
| V | TDD (NON-NEGOTIABLE) | PASS | All implementation phases preceded by test tasks. TDD Red-Green cycle explicit in task ordering. |
| VI | Behavior-Driven Development | PASS | All 8 user stories have Given-When-Then scenarios in spec.md. Integration/E2E tests validate these scenarios. |
| VII | Code Quality Standards | PASS | ArchUnit enforcement (T013-T017). Coverage validation (T126). Clean code via Java records. |

**Constitution violations found**: 0

---

## Unmapped Tasks

These tasks serve infrastructure/polish purposes without direct FR/SC mapping (acceptable):

| Task | Purpose |
|------|---------|
| T001-T006 | Project setup (Maven, Kind, Makefile, Docker, K8s namespace, proto) |
| T007-T012 | Service module skeletons |
| T013-T019 | ArchUnit tests, base configs, application classes |
| T123-T124 | Kind setup script, docker-compose |
| T125-T128 | Polish (ArchUnit validation, coverage, quickstart, Makefile) |

---

## Metrics

| Metric | Value |
|--------|-------|
| Total Functional Requirements (FRs) | 20 |
| Total Success Criteria (SCs) | 14 |
| Total User Stories | 8 |
| Total Tasks | 128 |
| FR Coverage (FRs with >= 1 task) | 19/20 (95%) |
| FR Full Coverage (explicit task) | 19/20 (95%) |
| FR Partial Coverage (implicit only) | 1/20 (5%) — FR-020 |
| SC with verification test | 11/14 (79%) |
| SC without explicit test | 3/14 — SC-010, SC-011, SC-013 |
| Constitution Violations | 0 |
| Critical Issues | 0 |
| High Issues | 4 |
| Medium Issues | 4 |
| Low Issues | 2 |
| Total Findings | 10 |
| Parallelizable Tasks | 89/128 (70%) |

---

## Next Actions

### Before `/speckit.implement` (Recommended)

No CRITICAL issues block implementation, but the 4 HIGH findings should be resolved to prevent confusion and rework during implementation:

1. **F1 + F6**: Update `plan.md` Payment Service structure to add `adapter/in/messaging/` and `HandleOrderCreatedUseCase` inbound port. Add corresponding task in `tasks.md` Phase 4.

2. **F2**: Decide on Order SHIPPED status trigger. Options:
   - (A) Add `ShipmentArrangedEvent` from Shipping back to Order via Kafka
   - (B) Remove SHIPPED from Order status machine (scope it out of PoC)
   - (C) Accept as a known PoC limitation and document in spec assumptions

3. **F3**: Clarify dual payment trigger in spec. Add assumption or clarification:
   - REST path (US1) is the primary sync payment flow
   - Kafka `order.created` consumer in Payment is an alternative async demonstration
   - Document that both paths exist for pattern comparison, not simultaneous production use

4. **F4**: Add explicit DLQ configuration tasks to `tasks.md` Phase 4.

### Optional Improvements

5. **F5**: Clean up `plan.md` by removing Order Service GraphQL adapter listing.
6. **F7**: Add log correlation test to Phase 7.
7. **F8**: Add timing assertion to quickstart validation.

### Suggested Commands

```bash
# After resolving HIGH findings:
/speckit.implement    # Begin implementation

# If plan.md changes are needed:
# Manually edit plan.md and tasks.md, then re-run:
/speckit.analyze      # Verify fixes
```

---

## Remediation Offer

Would you like me to suggest concrete remediation edits for the top 4 HIGH issues (F1-F4)? I will not apply them automatically -- edits require your explicit approval.
