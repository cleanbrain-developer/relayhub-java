# Spec 003: Kafka-Backed Delivery via Transactional Outbox

## Summary

Replaces Spec 002's in-process, synchronous retry loop (which runs inside the ingress request and
is not durable across a crash mid-retry — see ADR-0003) with the durable pipeline
`docs/architecture/system-design.md` always intended:

```text
Ingress Transaction
    -> Event stored
    -> Outbox stored in the same transaction
    -> Outbox Publisher
    -> Kafka
    -> Delivery Worker
    -> Target API
```

Spec 002's `Delivery`/`DeliveryAttempt`/retry/DLQ/Replay model is **reused, not replaced** — only
*what triggers* `DeliveryService.deliver(...)` changes, from "called inline during ingress" to
"called by a Kafka consumer after the Outbox Publisher hands it a message."

## Goal

Close the durability gap ADR-0003 flagged: a delivery task must survive a crash between "Event
committed" and "Target actually called," without depending on the ingress HTTP request staying
alive for the whole retry loop.

## In scope

- `OutboxEvent`: one row per (Event, Subscription) delivery task, written in the **same database
  transaction** as the Canonical Event during ingress. This is the actual transactional guarantee
  — if the Event commits, its delivery tasks are guaranteed to exist on disk, Kafka or no Kafka.
- `OutboxPublisher`: a scheduled poller (`@Scheduled`) that reads unpublished `OutboxEvent` rows,
  publishes one Kafka message per row to the `relayhub.delivery-tasks` topic, and marks the row
  published. Publish failure leaves the row `PENDING` for the next poll (at-least-once).
- `DeliveryWorker`: a `@KafkaListener` on `relayhub.delivery-tasks` that loads the Event and
  Subscription by ID and calls the **existing** `DeliveryService.deliver(...)` — Spec 002's
  retry/backoff/DLQ logic runs unchanged, just off a Kafka consumer thread instead of the ingress
  request thread.
- `IngressService.handle(...)` no longer calls `DeliveryService.deliver(...)` directly. It writes
  one `OutboxEvent` per active Subscription instead, in the same transaction as the Event.
- `docker-compose.yml` gains a single-node KRaft Kafka service (no Zookeeper).
- Tests use Spring Kafka's `@EmbeddedKafka` (in-process broker, no Docker required for
  `./gradlew test`), with Awaitility-style polling for the now-asynchronous delivery outcome.

## Deliberately out of scope

- Consumer-side idempotent processing beyond what Spec 002 already has (a delivery task consumed
  twice — e.g. after a worker crash and Kafka redelivery — creates a second `Delivery` row for the
  same (Event, Subscription) pair; Spec 002's idempotency dedup is at the *ingress* level, keyed on
  `(sourceEventId, idempotencyKey)`, not at the delivery-task level). Flagged as a known gap, not
  silently solved here — see "Known constraints" in `docs/status/current-state.md` after this spec.
- Kafka topic partitioning/ordering guarantees beyond a single partition; scaling the worker beyond
  one instance is not attempted.
- Outbox row cleanup/archival — published rows accumulate. Acceptable for a local MVP; revisit if
  this ever runs somewhere long-lived.
- Exactly-once Kafka semantics (transactional producer). At-least-once is the stated MVP policy
  (`docs/architecture/system-design.md`) and Spec 002's idempotency dedup already exists for the
  ingress-level duplicate case this most commonly matters for.

## Acceptance scenario

Extends Spec 001/002's scenario with the new asynchronous hop:

1. Register Source/Source Event/Target A (always succeeds)/Target B (fails 3x)/Subscriptions, same
   as Spec 002.
2. POST to the Ingress URL. The response no longer implies delivery is complete — it confirms the
   Event and its Outbox rows were durably stored (still HTTP 202).
3. Await (poll) until both Deliveries reach a terminal state: Target A `SUCCEEDED`, Target B `DEAD`
   after 3 attempts — now produced by `OutboxPublisher` -> Kafka -> `DeliveryWorker`, not by the
   ingress request itself.
4. Replay Target B's `DEAD` delivery via the existing `POST /api/deliveries/{id}/replay` endpoint
   (synchronous, unchanged from Spec 002) — confirms Spec 002's replay path still works unmodified
   on top of the new pipeline.
5. Duplicate ingress with the same idempotency key still dedups at ingress (Spec 002's guarantee),
   so it creates no new Outbox rows either.
