> 이 문서는 [`plan.md`](plan.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Spec 003: Implementation Plan

**Branch**: `003-kafka-outbox` | **Date**: 2026-09-10 | **Spec**: [link to ../spec.md](spec.md)

**Input**: 기록되지 않음 — 이 spec은 Spec Kit 도입 이전에 작성됨

1. `build.gradle.kts`: `spring-kafka`를 추가한다; `testImplementation`에 (`@EmbeddedKafka`를 위한)
   `spring-kafka-test`와 (async outcome에 대한 polling assertion을 위한) `org.awaitility:awaitility`를
   추가한다.
2. `docker-compose.yml`: single-node KRaft Kafka service(`apache/kafka`, Zookeeper 없음)를 추가한다.
3. `outbox` package: `OutboxStatus` enum (`PENDING`, `PUBLISHED`), `OutboxEvent` entity
   (`eventId`, `subscriptionId`, `status`, `createdAt`, `publishedAt`), `OutboxEventRepository`
   (`findTop50ByStatusOrderByCreatedAtAsc`)를 추가한다.
4. `IngressService.handle(...)`: 직접 `deliveryService.deliver(...)` loop를 호출하는 대신, Event
   저장과 동일한 `@Transactional` 메서드 안에서 활성 Subscription마다
   `outboxEventRepository.save(...)`를 호출하도록 바꾼다 (이것이 바로 transactional-outbox
   보장이다 — 별도의 transaction이 없다).
5. `outbox.OutboxPublisher`: `@Scheduled(fixedDelay=...)`로 `PENDING` row를 폴링하여,
   `KafkaTemplate`을 통해 `relayhub.delivery-tasks`에 `DeliveryTaskMessage(outboxId, eventId, subscriptionId)`를
   publish하고, 전송에 성공하면 row를 `PUBLISHED`로 표시한다.
6. `delivery.DeliveryTaskMessage` record와 `delivery.DeliveryWorker` (`@KafkaListener`): ID로
   Event와 Subscription을 로드하여, 기존의 `DeliveryService.deliver(...)`를 변경 없이 호출한다.
7. `application.yml`: `spring.kafka.bootstrap-servers`, JSON (de)serializer 설정, consumer group id를
   설정한다.
8. `IngressVerticalSliceTest`와 `DlqReplayIdempotencyTest`를 (혹은 Spec-003 전용 새 테스트를)
   `@EmbeddedKafka`와 Awaitility polling을 사용하도록 재작업하여, ingress POST 직후 synchronous하게
   완료를 assert하는 대신 이를 사용하도록 한다.
