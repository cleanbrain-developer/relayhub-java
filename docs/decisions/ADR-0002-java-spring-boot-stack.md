# ADR-0002: Java 21 / Spring Boot Core Stack

- Status: Accepted
- Date: 2026-09-10
- Deciders: Project maintainer

## Context

RelayHub's design was drafted with a Java 21 / Spring Boot 3.x core stack (Gradle Kotlin DSL, PostgreSQL, Redis, Kafka, Resilience4j, Jackson/JSONPath, OpenAPI/JSON Schema, JUnit 5/Testcontainers, Micrometer/OpenTelemetry/Prometheus/Grafana, Docker Compose).

During adoption planning, the maintainer considered building this first implementation in NestJS/TypeScript instead — mainly for language consistency with other `cleanbrain.me` services and reuse of already-verified Node deployment patterns.

The original design explicitly states a secondary project goal: demonstrating production-level Java/Spring Boot backend capability. A NestJS implementation would not serve that goal. The maintainer decided to keep this repository (`relayhub-java`) as the Java/Spring Boot implementation, and to build any other-language implementation of the same product design (e.g. Node/NestJS) as a **separate, sibling repository** rather than switching this one.

## Decision

- This repository (`relayhub-java`) implements RelayHub's core on Java 21 / Spring Boot 3.x, per the original design's full stack table (see `docs/architecture/system-design.md`).
- A same-product implementation in a different language is out of scope for this repository. It becomes a separate repository (e.g. `relayhub-node`), with its own hostname (e.g. `relayhub-node.developer.cleanbrain.me`) following the `relayhub-<lang>.developer.cleanbrain.me` naming convention, so multiple language implementations of the same design can coexist without ambiguity.
- Kotlin may be selectively adopted later for small connectors/extensions within this repository; the core implementation language stays Java.

## Consequences

### Positive

- The stated portfolio goal (demonstrating Java/Spring Boot backend capability) is preserved.
- The original design document's stack table, reliability architecture (Transactional Outbox, Kafka, Resilience4j), and module layout apply directly without rework.
- Future language variants have a defined, symmetric naming pattern instead of ad hoc naming.

### Costs and risks

- Building a second, same-purpose implementation later duplicates domain design work across repositories (mitigated by both implementations sharing the same product design captured in `docs/product/`, adapted per repository).
- `cleanbrain-me-infra`'s naming conventions must accommodate the `<service>-<lang>.developer.cleanbrain.me` pattern, which is new relative to its existing flat (`english-core-speaking.cleanbrain.me`) and single-subdomain-namespace (`kioti.cleanbrain.me`) patterns.

## Alternatives considered

### NestJS/TypeScript for this repository

Rejected for this repository specifically because it would not serve the stated Java/Spring Boot capability goal. Not rejected as a future RelayHub variant — see "Decision" above.

### Rename this repository once a second language variant exists, instead of naming it `-java` now

Rejected because it defers a rename that is already known to be needed, and `relayhub-java` is unambiguous from the start with no migration cost later.
