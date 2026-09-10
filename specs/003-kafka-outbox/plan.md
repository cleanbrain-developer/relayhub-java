# Spec 003: Implementation Plan

1. `build.gradle.kts`: add `spring-kafka`; `testImplementation` `spring-kafka-test` (for
   `@EmbeddedKafka`) and `org.awaitility:awaitility` (for polling assertions on async outcomes).
2. `docker-compose.yml`: add a single-node KRaft Kafka service (`apache/kafka`, no Zookeeper).
3. `outbox` package: `OutboxStatus` enum (`PENDING`, `PUBLISHED`), `OutboxEvent` entity
   (`eventId`, `subscriptionId`, `status`, `createdAt`, `publishedAt`), `OutboxEventRepository`
   (`findTop50ByStatusOrderByCreatedAtAsc`).
4. `IngressService.handle(...)`: replace the direct `deliveryService.deliver(...)` loop with
   `outboxEventRepository.save(...)` per active Subscription, in the same `@Transactional` method
   as the Event save (this *is* the transactional-outbox guarantee — no separate transaction).
5. `outbox.OutboxPublisher`: `@Scheduled(fixedDelay=...)`, polls `PENDING` rows, publishes a
   `DeliveryTaskMessage(outboxId, eventId, subscriptionId)` to `relayhub.delivery-tasks` via
   `KafkaTemplate`, marks the row `PUBLISHED` on send success.
6. `delivery.DeliveryTaskMessage` record + `delivery.DeliveryWorker` (`@KafkaListener`): loads
   Event + Subscription by ID, calls the existing `DeliveryService.deliver(...)` unchanged.
7. `application.yml`: `spring.kafka.bootstrap-servers`, JSON (de)serializer config, consumer
   group id.
8. Rework `IngressVerticalSliceTest` and `DlqReplayIdempotencyTest` (or add new Spec-003-specific
   tests) to use `@EmbeddedKafka` and Awaitility polling instead of asserting synchronous
   completion right after the ingress POST.
