> 이 문서는 [`ADR-0002-java-spring-boot-stack.md`](ADR-0002-java-spring-boot-stack.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# ADR-0002: Java 21 / Spring Boot Core Stack

- Status: Accepted
- Date: 2026-09-10
- Deciders: Project maintainer

## Context

RelayHub의 design은 Java 21 / Spring Boot 3.x core stack(Gradle Kotlin DSL, PostgreSQL, Redis, Kafka, Resilience4j, Jackson/JSONPath, OpenAPI/JSON Schema, JUnit 5/Testcontainers, Micrometer/OpenTelemetry/Prometheus/Grafana, Docker Compose)으로 작성되었습니다.

adoption을 계획하는 동안, maintainer는 이 첫 implementation을 NestJS/TypeScript로 만드는 것을 고려했습니다 — 주로 다른 `cleanbrain.me` service와의 language consistency와 이미 검증된 Node 배포 패턴의 재사용을 위해서였습니다.

원래의 design은 production-level Java/Spring Boot backend 역량을 증명한다는 secondary project goal을 명시적으로 밝히고 있습니다. NestJS implementation은 그 목표를 충족하지 못합니다. maintainer는 이 repository(`relayhub-java`)를 Java/Spring Boot implementation으로 유지하고, 동일한 product design의 다른 언어 implementation(예: Node/NestJS)은 이 repository를 전환하는 대신 **별도의 sibling repository**로 만들기로 결정했습니다.

## Decision

- 이 repository(`relayhub-java`)는 원래 design의 전체 stack table을 따라 RelayHub의 core를 Java 21 / Spring Boot 3.x로 구현합니다(`docs/architecture/system-design.md` 참고).
- 동일한 product의 다른 언어 implementation은 이 repository의 범위 밖입니다. 그것은 별도의 repository(예: `relayhub-node`)가 되며, 여러 언어 implementation이 모호함 없이 공존할 수 있도록 `relayhub-<lang>.developer.cleanbrain.me` naming convention을 따르는 자체 hostname(예: `relayhub-node.developer.cleanbrain.me`)을 가집니다.
- Kotlin은 이 repository 내에서 작은 connector/extension에 나중에 선택적으로 채택될 수 있으며, core implementation language는 Java로 유지됩니다.

## Consequences

### Positive

- 명시된 portfolio goal(Java/Spring Boot backend 역량 증명)이 보존됩니다.
- 원래 design document의 stack table, reliability architecture(Transactional Outbox, Kafka, Resilience4j), module layout이 재작업 없이 그대로 적용됩니다.
- 미래의 language variant는 ad hoc한 naming이 아니라 정의되고 대칭적인 naming pattern을 가집니다.

### Costs and risks

- 나중에 동일한 목적의 두 번째 implementation을 만들면 repository 간에 domain design 작업이 중복됩니다(두 implementation이 `docs/product/`에 담긴 동일한 product design을 공유하고 repository별로 조정한다는 점으로 완화됨).
- `cleanbrain-me-infra`의 naming convention은 `<service>-<lang>.developer.cleanbrain.me` 패턴을 수용해야 하며, 이는 기존의 flat 패턴(`english-core-speaking.cleanbrain.me`) 및 single-subdomain-namespace 패턴(`kioti.cleanbrain.me`)에 비해 새로운 것입니다.

## Alternatives considered

### NestJS/TypeScript for this repository

이 repository에 한해서는 명시된 Java/Spring Boot 역량 목표를 충족하지 못하기 때문에 rejected되었습니다. 미래의 RelayHub variant로서는 rejected된 것이 아닙니다 — 위의 "Decision" 참고.

### Rename this repository once a second language variant exists, instead of naming it `-java` now

이미 필요하다고 알려진 rename을 미루는 것이기 때문에 rejected되었습니다. `relayhub-java`는 처음부터 모호하지 않으며 나중에 migration cost가 없습니다.
