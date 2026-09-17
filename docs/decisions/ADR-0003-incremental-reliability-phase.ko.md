> 이 문서는 [`ADR-0003-incremental-reliability-phase.md`](ADR-0003-incremental-reliability-phase.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# ADR-0003: Split the Reliability Phase into Incremental Specs

- Status: Accepted
- Date: 2026-09-10
- Deciders: Project maintainer

## Context

(원래의 design context에서 가져온) `docs/architecture/system-design.md`는 "Phase 2: Reliability"를 하나의 묶음 unit으로 설명합니다: Kafka 기반 internal delivery, Transactional Outbox pattern, Retry/Backoff, DLQ, Replay, Idempotency 강제. 이 모든 것을 한 번의 change로 구현하는 것은 크고 review하기 어려운 unit이며, 서로 다른 두 종류의 risk를 섞습니다: (1) 기존의 synchronous request flow 안에서 완전히 구축하고 test할 수 있는 delivery semantics(retry, failure visibility, recovery), (2) runtime topology와 local Docker Compose setup을 바꾸는 새로운 infrastructure(Kafka, Outbox table, publisher/consumer 분리).

maintainer는 전체 bundle을 한 번에 만들지 아니면 분리할지 선택하도록 요청받았고, 분리하기로 결정했습니다.

## Decision

Reliability phase를 최소 두 개의 spec으로 분리합니다:

- **Spec 002** (`specs/002-retry-dlq-replay/`): `Delivery` state(`PENDING`/`SUCCEEDED`/`DEAD`), in-process Retry + Backoff, `Delivery` state로서의 DLQ, Operator가 트리거하는 Replay, ingress에서의 idempotency-key deduplication. 새로운 infrastructure는 없으며, Spec 001과 마찬가지로 delivery는 여전히 ingress request 안에서 synchronous하게 일어납니다.
- **Spec 003+** (아직 시작하지 않음): Kafka 기반 internal delivery와 Transactional Outbox pattern으로, Spec 002의 in-process retry loop를 `docs/architecture/system-design.md`에 설명된 durable `Ingress Transaction -> Event -> Outbox -> Publisher -> Kafka -> Delivery Worker -> Target API` pipeline으로 대체합니다.

이것은 `system-design.md`의 target architecture를 바꾸지 않습니다 — 구축되고 검증되는 순서와 granularity만 바꿉니다. Spec 002의 `Delivery` entity와 state는, Spec 003이 attempt 실행을 request thread에서 Kafka consumer로 옮긴 뒤에도 대체되지 않고 재사용되도록 설계되었습니다.

## Consequences

### Positive

- 각 spec은 독립적으로 test 가능하고 되돌릴 수 있습니다; Spec 002는 새로운 infrastructure가 도입되기 전에 실제 integration test(의도적인 Target failure -> retry -> DLQ -> replay)와 함께 출시되었습니다.
- constitution이 feature 수보다 우선시하는 failure-visibility와 recovery 속성이, risk가 더 높은 infrastructure 변경보다 먼저 도착합니다.

### Costs and risks

- Spec 002의 retry는 synchronous/in-process합니다: `PENDING` delivery는 retry 도중 JVM crash에도 durable하지 않습니다(`DEAD`/`SUCCEEDED`만 안전하게 persist되는 terminal state입니다). 이 gap은 Spec 002에서 조용히 우회할 결함이 아니라, 명시적으로 Spec 003이 존재하는 이유입니다.
- `Subscription.retryPolicy`는 Spec 002에서 아직 실제 per-subscription policy로 parse되지 않습니다; 고정된 constant policy(3회 시도, 200ms/400ms backoff)가 모든 Subscription에 적용됩니다. 실제 사용이 무엇을 configurable하게 만들어야 하는지 보여주면 재검토합니다.

## Alternatives considered

### Build all of Phase 2 (Kafka + Outbox + Retry + DLQ + Replay + Idempotency) in one change

이것은 `system-design.md`가 원래 하나의 phase로 설명했던 것입니다. 규모가 크고, 서로 실제로 의존하지 않는 infrastructure 변경과 delivery-semantics 변경을 결합시키기 때문에 바로 다음 단계로는 rejected되었습니다.
