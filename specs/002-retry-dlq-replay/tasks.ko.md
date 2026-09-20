> 이 문서는 [`tasks.md`](tasks.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Spec 002: Tasks

**Input**: Design documents from `specs/002-retry-dlq-replay/`

- [x] `Delivery` entity, `DeliveryState` enum, `DeliveryRepository`.
- [x] `DeliveryAttempt.deliveryId` FK.
- [x] Backoff를 포함한 `DeliveryService` retry loop, `Delivery` state 전이.
- [x] `DeliveryService.replay(deliveryId)`.
- [x] `DeliveryController`: `Delivery`-level 목록, attempt-level 목록, replay endpoint.
- [x] `IngressService` + `EventRepository` query에서의 idempotency dedup.
- [x] Integration test: Target B가 3회 실패 -> `DEAD`, replay -> `SUCCEEDED`.
- [x] Integration test: 중복 idempotency key가 두 번째 Event/Delivery를 생성하지 않음.

`DlqReplayIdempotencyTest`(H2 기반, `./gradlew test`)로 전부 검증되었고, 실제 Postgres에 대해서도
수동으로 검증되었다 (2026-09-10, Postgres 전용 `Delivery.createdAt` flush-timing 버그 수정 포함) —
자세한 내용은 `docs/status/current-state.md` 참고.
