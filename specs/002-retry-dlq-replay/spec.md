# Spec 002: Retry, DLQ, Replay, Idempotency (without Kafka/Outbox yet)

## Summary

Hardens Spec 001's single-attempt synchronous delivery into a retryable, observable, recoverable
one: failed deliveries are retried with backoff, exhausted deliveries land in a dead-letter state
(DLQ) instead of being silently lost, an Operator can manually replay a dead delivery, and a
Source resending the same event (by idempotency key) does not create a duplicate Canonical Event
or duplicate deliveries.

Kafka and the Transactional Outbox pattern are **explicitly deferred** to Spec 003 — see
"Deliberately out of scope" below and `docs/decisions/ADR-0003-incremental-reliability-phase.md`
for why this was split instead of building `docs/architecture/system-design.md`'s full Phase 2 in
one change.

## Goal

Make failure observable and recoverable (constitution: "Observable failure over hidden failure",
"Replayable structure over manual correction") before introducing new infrastructure (Kafka).

## In scope

- A `Delivery` entity representing one (Event, Subscription) delivery unit, with state
  `PENDING` (attempts still possible within the current retry loop) -> `SUCCEEDED` or `DEAD`.
- Retry with backoff, executed **synchronously within the ingress request** (no scheduler/queue
  yet — see "Deliberately out of scope"): up to a fixed MVP policy of 3 attempts, backoff
  200ms/400ms between attempts. Every attempt is still recorded as a `DeliveryAttempt` row
  (Spec 001's model), now linked to its parent `Delivery`.
- DLQ as a `Delivery` state (`DEAD`), not a separate queue/table — simplest representation that
  still satisfies "failures must be visible and recoverable."
- `POST /api/deliveries/{deliveryId}/replay`: re-attempts a `DEAD` delivery once; on success moves
  it to `SUCCEEDED` and appends a new `DeliveryAttempt`; on failure it stays `DEAD` and the failed
  attempt is still recorded.
- `GET /api/deliveries?eventId=` returns `Delivery` records (not raw attempts) so an Operator sees
  overall status per Target at a glance; attempt-level detail stays queryable via
  `GET /api/deliveries/{deliveryId}/attempts`.
- Idempotency enforcement at ingress: if a Source Event has `idempotencyKeyPath`/`idempotencyHeader`
  configured and a Canonical Event already exists for the same `(sourceEventId, idempotencyKey)`,
  the Ingress endpoint returns the existing Event (200, not 202) and does **not** create new
  Deliveries — it is not treated as a new event.

## Deliberately out of scope (Spec 003+)

- Kafka-backed internal delivery and the Transactional Outbox pattern — retries here run
  in-process/synchronously, not via a message broker or a scheduled worker. This means a delivery
  that is still `PENDING` when the JVM crashes mid-retry is not recovered automatically; only
  `DEAD` (exhausted) and terminal states are durable and safe. This is the actual gap Spec 003's
  Outbox/Kafka pipeline exists to close, per `docs/architecture/system-design.md`.
- Configurable per-Subscription retry policy parsing (`Subscription.retryPolicy` stays a
  free-text/description field; a fixed constant policy applies to every Subscription in this spec).
- Automatic/scheduled replay — replay is operator-triggered only (`POST .../replay`), matching the
  MVP policy table in `docs/architecture/system-design.md` ("Recovery: Operator-triggered manual
  Replay").
- Idempotency across process restarts under concurrent duplicate requests (no distributed lock);
  the uniqueness check is a plain read-then-write, acceptable for a single-instance MVP.

## Acceptance scenario

Extends Spec 001's scenario with the Target B failure path from the original design context
(`docs/product/overview.md` "V1 experience", steps 4-7):

1. Register `demo-source`/`customer-created` as in Spec 001, plus a second Target `demo-target-b`
   and Subscription that will fail every attempt (stubbed to return HTTP 500).
2. POST a Source payload to the Ingress URL. Target A succeeds; Target B's Delivery goes through
   3 failed attempts (with backoff) and lands in `DEAD`.
3. `GET /api/deliveries?eventId=` shows one `SUCCEEDED` Delivery (Target A) and one `DEAD` Delivery
   (Target B) with 3 recorded `DeliveryAttempt`s.
4. Reconfigure Target B's stub to succeed. `POST /api/deliveries/{targetB-deliveryId}/replay`.
5. Target B's Delivery is now `SUCCEEDED`, with a 4th `DeliveryAttempt` recorded.
6. Re-POST the same Source payload with the same idempotency key. The response reflects the
   existing Event (no new Event, no new Deliveries created).
