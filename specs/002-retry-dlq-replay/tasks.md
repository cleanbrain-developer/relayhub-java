# Spec 002: Tasks

- [x] `Delivery` entity, `DeliveryState` enum, `DeliveryRepository`.
- [x] `DeliveryAttempt.deliveryId` FK.
- [x] `DeliveryService` retry loop with backoff, `Delivery` state transitions.
- [x] `DeliveryService.replay(deliveryId)`.
- [x] `DeliveryController`: `Delivery`-level list, attempt-level list, replay endpoint.
- [x] Idempotency dedup in `IngressService` + `EventRepository` query.
- [x] Integration test: Target B fails 3x -> `DEAD`, replay -> `SUCCEEDED`.
- [x] Integration test: duplicate idempotency key does not create a second Event/Delivery.

All verified by `DlqReplayIdempotencyTest` (H2-backed, `./gradlew test`), not just compiled. Not yet re-verified against real Postgres — see `docs/status/current-state.md`.
