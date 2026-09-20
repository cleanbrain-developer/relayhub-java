> 이 문서는 [`tasks.md`](tasks.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Spec 003: Tasks

**Input**: Design documents from `specs/003-kafka-outbox/`

- [x] `spring-kafka`/`spring-kafka-test`/`awaitility` 의존성.
- [x] `docker-compose.yml` Kafka service (KRaft, single node, `apache/kafka:3.8.0`).
- [x] `OutboxStatus`, `OutboxEvent`, `OutboxEventRepository`.
- [x] `IngressService`가 `DeliveryService.deliver(...)`를 직접 호출하는 대신 Outbox row를 기록한다.
- [x] `OutboxPublisher` (scheduled, poll + publish + mark published).
- [x] `DeliveryTaskMessage`, `DeliveryWorker` (`@KafkaListener` -> `DeliveryService.deliver(...)`).
- [x] `application.yml` Kafka 설정.
- [x] 비동기 경로에 맞게 업데이트된 테스트 (`@EmbeddedKafka` + Awaitility): Target A 성공,
      Target B가 3회 실패 -> DEAD -> replay -> SUCCEEDED, 중복 idempotency key는 여전히 dedup됨.
- [x] `docker compose up -d` + `bootRun`을 통한 실제 Kafka+Postgres 수동 검증 (2026-09-10):
      curl로 전체 시나리오를 재현했고, Postgres에서 직접 `outbox_events`가 `PUBLISHED`임을 확인했다.

초기 H2 전용 개발이 아니라 실제 Kafka/Postgres/multi-context 테스트 실행을 통해서만 발견되고 수정된
두 가지 실제 버그가 있다 — 둘 다 자세한 내용은 `docs/status/current-state.md` 참고
(H2 cross-context table drop, `DeliveryWorker`에서의 `LazyInitializationException`).
