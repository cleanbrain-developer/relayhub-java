# Spec 002: Implementation Plan

1. `delivery.DeliveryState` enum (`PENDING`, `SUCCEEDED`, `DEAD`) and `delivery.Delivery` entity
   (`eventId`, `subscriptionId`, `targetId`, `state`, `attemptCount`, timestamps) + repository.
2. Add `deliveryId` FK to `DeliveryAttempt`; keep its existing per-attempt fields.
3. Rewrite `DeliveryService.deliver(...)` to: create a `Delivery` row, run a bounded retry loop
   (3 attempts, 200ms/400ms backoff) calling the existing mapping/HTTP-call logic per attempt,
   record a `DeliveryAttempt` per try, and set the `Delivery`'s final state.
4. Add `DeliveryService.replay(deliveryId)`: load a `DEAD` `Delivery`, re-run one attempt, update
   state accordingly.
5. `DeliveryController`: change `GET /api/deliveries?eventId=` to return `Delivery` summaries; add
   `GET /api/deliveries/{deliveryId}/attempts` for attempt-level history; add
   `POST /api/deliveries/{deliveryId}/replay`.
6. `EventRepository.findBySourceEventIdAndIdempotencyKey(...)`; `IngressService.handle(...)`
   checks it before creating a new Event when an idempotency key is present.
7. Extend the integration test suite with the Target-B failure -> retry -> DLQ -> replay path,
   plus a duplicate-idempotency-key ingress request.
