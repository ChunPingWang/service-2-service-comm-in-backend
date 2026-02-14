<!--
=== Sync Impact Report ===
Version change: (new) → 1.0.0
Modified principles: N/A (initial creation)
Added sections:
  - Core Principles (7 principles)
  - Architecture Constraints
  - Development Workflow & Quality Gates
  - Governance
Removed sections: N/A
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ no update needed
    (Constitution Check section is dynamically filled)
  - .specify/templates/spec-template.md ✅ no update needed
    (BDD Given-When-Then format already present in template)
  - .specify/templates/tasks-template.md ✅ no update needed
    (Test-first workflow already referenced in template)
  - .specify/templates/checklist-template.md ✅ no update needed
    (Generic template, checklist generated dynamically)
  - .specify/templates/agent-file-template.md ✅ no update needed
    (Generic template, filled per project)
Follow-up TODOs: None
===========================
-->

# Service-to-Service Communication PoC Constitution

## Core Principles

### I. Hexagonal Architecture

All services MUST adopt Hexagonal Architecture (Ports & Adapters).
The architecture is organized in three concentric layers:

- **Domain Layer (innermost)**: Contains domain models, domain events,
  value objects, and domain services. MUST have zero dependencies on
  any framework, library, or infrastructure concern.
- **Application Layer (middle)**: Contains use cases, application
  services, and port interfaces (both inbound and outbound). MUST
  depend only on the Domain Layer. MUST NOT import any infrastructure
  or framework classes.
- **Infrastructure Layer (outermost)**: Contains adapters (REST
  controllers, gRPC services, Kafka consumers/producers, database
  repositories, external API clients), framework configurations, and
  all third-party library integrations. MAY directly reference
  Application Layer and Domain Layer types.

All frameworks (Spring Boot, gRPC, Kafka, etc.) MUST reside exclusively
in the Infrastructure Layer. No framework annotation or class is
permitted in Application or Domain layers.

### II. Layer Isolation & Dependency Inversion

The dependency direction MUST always point inward: Infrastructure →
Application → Domain. The reverse direction is strictly forbidden.

- Application and Domain layers MUST access Infrastructure capabilities
  exclusively through port interfaces defined in the Application Layer.
- Infrastructure layer implements these port interfaces as adapters.
- Data crossing layer boundaries MUST be transformed via dedicated
  mapper classes. Domain objects MUST NOT leak into API responses or
  messaging payloads directly. Each boundary requires an explicit
  mapper (e.g., `OrderMapper`, `PaymentDtoMapper`).
- Inbound adapters convert external requests into application-layer
  commands/queries via mappers before invoking use case ports.
- Outbound adapters convert domain/application objects into
  infrastructure-specific formats via mappers.

### III. SOLID Principles

All code MUST adhere to the five SOLID principles:

- **Single Responsibility (SRP)**: Each class MUST have exactly one
  reason to change. Use cases, mappers, adapters, and domain services
  MUST be separate classes.
- **Open/Closed (OCP)**: Modules MUST be open for extension but closed
  for modification. New communication protocols or adapters MUST be
  addable without modifying existing use case logic.
- **Liskov Substitution (LSP)**: Subtypes MUST be substitutable for
  their base types without altering correctness. Port interface
  implementations MUST honor the contract defined by the interface.
- **Interface Segregation (ISP)**: Clients MUST NOT be forced to depend
  on interfaces they do not use. Port interfaces MUST be granular
  (e.g., separate `ProductQueryPort` and `ProductCommandPort` rather
  than a single `ProductPort`).
- **Dependency Inversion (DIP)**: High-level modules (Application,
  Domain) MUST NOT depend on low-level modules (Infrastructure). Both
  MUST depend on abstractions (port interfaces). This principle is
  the foundation of Principle II.

### IV. Domain-Driven Design

Each microservice MUST model its bounded context with explicit
domain boundaries:

- **Ubiquitous Language**: Code identifiers (class names, method names,
  variables) MUST use the domain language defined in the PRD. No
  technical jargon in domain model naming.
- **Bounded Contexts**: Each service (Order, Product, Payment,
  Notification, Shipping) represents a distinct bounded context.
  Cross-context communication MUST occur through well-defined
  interfaces (synchronous ports or asynchronous domain events).
- **Domain Events**: State transitions that other bounded contexts
  need to know about MUST be modeled as domain events
  (e.g., `OrderCreatedEvent`, `PaymentCompletedEvent`).
- **Value Objects**: Immutable domain concepts (e.g., `Money`,
  `OrderId`, `ProductId`) MUST be modeled as value objects with
  structural equality.
- **Aggregates**: Each aggregate MUST enforce its own invariants.
  External access to aggregate internals is forbidden; operations
  go through the aggregate root.

### V. Test-Driven Development (NON-NEGOTIABLE)

TDD is mandatory for all production code. The Red-Green-Refactor
cycle MUST be strictly followed:

1. **Red**: Write a failing test that defines the desired behavior.
2. **Green**: Write the minimum code to make the test pass.
3. **Refactor**: Improve code structure while keeping tests green.

Rules:

- No production code may be written without a corresponding failing
  test first.
- Tests MUST be approved/reviewed before implementation begins for
  each unit of work.
- Unit tests MUST cover domain logic and application services with
  no infrastructure dependencies (use test doubles for ports).
- Integration tests MUST use Testcontainers to verify adapter
  behavior against real infrastructure (Kafka, RabbitMQ, gRPC).
- E2E tests MUST verify complete business flows across services
  in a Kind cluster.
- Architecture tests (e.g., ArchUnit) MUST enforce hexagonal
  layer constraints automatically.

### VI. Behavior-Driven Development

All user-facing features MUST be specified using BDD scenarios
before implementation:

- Acceptance criteria MUST follow the **Given-When-Then** format.
- Each user story MUST have at least one BDD scenario that serves
  as both specification and acceptance test.
- BDD scenarios MUST be written in domain language (Ubiquitous
  Language), not technical language.
- Scenarios MUST be independently verifiable — each scenario
  describes a single behavior that can be tested in isolation.
- Edge cases and error scenarios MUST also be captured as
  Given-When-Then scenarios.

### VII. Code Quality Standards

All code MUST meet the following quality gates:

- **Test Coverage**: Line coverage MUST be >= 80% for domain and
  application layers. Integration test coverage for adapters MUST
  cover all happy paths and critical error paths.
- **Static Analysis**: Code MUST pass linting and static analysis
  without warnings. Suppressed warnings MUST include justification
  comments.
- **Clean Code**: Methods MUST NOT exceed 20 lines (excluding test
  methods). Classes MUST NOT exceed 200 lines. Cyclomatic complexity
  per method MUST NOT exceed 10.
- **Naming**: All identifiers MUST be self-documenting. Comments
  MUST explain "why", never "what". No abbreviations except
  universally understood ones (e.g., `id`, `dto`, `url`).
- **Immutability**: Domain objects and value objects MUST be
  immutable. Use Java records where applicable.
- **No Dead Code**: Unused imports, variables, methods, and classes
  MUST be removed. No commented-out code in main branches.

## Architecture Constraints

- **Technology Stack**: Java 23, Spring Boot 4, Maven, Testcontainers,
  JUnit 5, Kind (Kubernetes in Docker). All services MUST use this
  stack consistently.
- **Service Structure**: Every service MUST follow the directory
  convention: `adapter/in/`, `adapter/out/`, `application/port/in/`,
  `application/port/out/`, `application/service/`, `domain/model/`,
  `domain/event/`, `config/`.
- **Framework Boundary**: Spring annotations (`@Component`,
  `@Service`, `@RestController`, `@KafkaListener`, etc.) MUST
  appear only in adapter and config packages (Infrastructure Layer).
  Application and domain packages MUST be framework-free.
- **Port Interface Location**: All port interfaces MUST reside in
  the `application/port/` package. Inbound ports in `port/in/`,
  outbound ports in `port/out/`.
- **Mapper Location**: Mappers crossing layer boundaries MUST reside
  in the adapter package that initiates the conversion. Inbound
  mappers in `adapter/in/`, outbound mappers in `adapter/out/`.
- **No Cross-Service Domain Sharing**: Services MUST NOT share
  domain model classes. Shared contracts (Protobuf definitions,
  event schemas) reside in the `proto/` or shared schema directory,
  and each service maps them to its own domain types.

## Development Workflow & Quality Gates

- **Workflow**: Feature Branch → TDD Red Phase → TDD Green Phase →
  Refactor → Code Review → Merge. No direct commits to main.
- **Pre-Commit Gate**: All unit tests MUST pass. Linting and static
  analysis MUST pass. Architecture tests MUST pass.
- **Pre-Merge Gate**: All integration tests MUST pass via
  Testcontainers. All BDD acceptance scenarios MUST pass. Code
  review by at least one team member is required.
- **Commit Conventions**: Use conventional commits
  (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
  Each commit MUST be atomic and represent a single logical change.
- **Architecture Enforcement**: ArchUnit tests MUST be present in
  every service to automatically verify hexagonal layer constraints,
  dependency direction, and framework isolation rules.
- **Continuous Validation**: The CI pipeline MUST run unit tests,
  integration tests, architecture tests, and static analysis on
  every push. E2E tests run on merge to main.

## Governance

- This constitution supersedes all other development practices and
  guidelines for this project.
- Amendments MUST be documented with rationale, approved by the
  team lead, and accompanied by a migration plan for existing code.
- All pull requests and code reviews MUST verify compliance with
  these principles. Non-compliance MUST be flagged and resolved
  before merge.
- Complexity beyond what the constitution permits MUST be justified
  in writing (via the Complexity Tracking table in plan.md) with
  an explanation of why the simpler alternative is insufficient.
- Versioning follows Semantic Versioning: MAJOR for principle
  removals or redefinitions, MINOR for new principles or material
  expansions, PATCH for clarifications and wording fixes.
- Compliance review MUST be conducted at each sprint retrospective.
- Use CLAUDE.md or the agent guidance file for runtime development
  instructions that supplement (but do not override) this
  constitution.

**Version**: 1.0.0 | **Ratified**: 2026-02-14 | **Last Amended**: 2026-02-14
