# Spec 003: Tasks

- [x] `spring-kafka`/`spring-kafka-test`/`awaitility` dependencies.
- [x] `docker-compose.yml` Kafka service (KRaft, single node, `apache/kafka:3.8.0`).
- [x] `OutboxStatus`, `OutboxEvent`, `OutboxEventRepository`.
- [x] `IngressService` writes Outbox rows instead of calling `DeliveryService.deliver(...)` directly.
- [x] `OutboxPublisher` (scheduled, polls + publishes + marks published).
- [x] `DeliveryTaskMessage`, `DeliveryWorker` (`@KafkaListener` -> `DeliveryService.deliver(...)`).
- [x] `application.yml` Kafka config.
- [x] Tests updated for the async path (`@EmbeddedKafka` + Awaitility): Target A succeeds,
      Target B fails x3 -> DEAD -> replay -> SUCCEEDED, duplicate idempotency key still dedups.
- [x] Manual real-Kafka+Postgres verification via `docker compose up -d` + `bootRun` (2026-09-10):
      full scenario replayed via curl, `outbox_events` confirmed `PUBLISHED` in Postgres directly.

Two real bugs found and fixed only by the real-Kafka/Postgres/multi-context test run, not by initial
H2-only development — see `docs/status/current-state.md` for both (H2 cross-context table drop,
`LazyInitializationException` in `DeliveryWorker`).
