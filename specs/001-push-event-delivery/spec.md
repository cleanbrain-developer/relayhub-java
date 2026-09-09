# Spec 001: Push Event Delivery (First Vertical Slice)

## Summary

The first thin, end-to-end vertical slice of RelayHub: register a Source/Source Event/Target/Subscription, receive a push event on an auto-generated Ingress URL, extract and standardize it into a Canonical Event, map it per Subscription, and deliver it to each Target over HTTP.

## Goal

Prove the full path works before adding reliability depth (Kafka, Transactional Outbox, retry/backoff, DLQ, replay — see Spec 002+, "Reliability phase" in `docs/product/goals.md`).

## In scope

- Source / Source Event / Target / Subscription registration APIs (create, read).
- Per-Source-Event Ingress URL, generated from registration data (not hardcoded).
- Receiving an arbitrary Source JSON payload on that URL.
- JSONPath extraction of `resourceIdPath` (required) and, if configured, `occurredAtPath` / `idempotencyKeyPath` / `idempotencyHeader`.
- Optional JSON Schema validation of the Source payload.
- Canonical Event construction and persistence (schema in `docs/architecture/system-design.md`).
- Subscription lookup for the triggering Source Event.
- Per-Subscription Target payload mapping via a JSON template, implemented with Jackson `JsonNode` tree manipulation (not string `replace()`).
- Synchronous-looking (in-process, not yet Kafka-backed) asynchronous HTTP delivery to each Target.
- Persisting the delivery result (success/failure) as a Delivery Attempt.

## Out of scope (deferred to later specs)

- Kafka-backed internal delivery and the Transactional Outbox pattern.
- Retry/backoff, DLQ, and manual Replay.
- Idempotency deduplication logic (the field is recorded on the Canonical Event, but no dedup enforcement yet).
- Structured metrics/tracing dashboards (basic structured logs only).
- Authentication/authorization on registration or Ingress APIs beyond what's needed to run the acceptance scenario locally.

## Acceptance scenario

See `docs/product/overview.md` ("V1 experience") for the full narrative. Concretely for this spec (single-Target version, since Retry/DLQ/Replay for the second Target is Spec 002+):

1. Register `demo-source` (Source), `customer-created` (Source Event: `resourceType=customer`, `operation=CREATED`, `ingressMethod=POST`, `resourceIdPath=$.customerNo`), `demo-target-a` (Target), and a Subscription linking them.
2. POST `{"customerNo":"C10001","name":"ABC Dealer","email":"dealer@example.com"}` to the generated Ingress URL.
3. RelayHub identifies Source/Event from the URL, extracts `resourceId=C10001` via JSONPath, builds and stores a Canonical Event.
4. RelayHub looks up the Subscription, maps the payload per its template, and calls `demo-target-a`'s HTTP endpoint.
5. The Delivery Attempt (success, HTTP 2xx) is stored and queryable alongside the Event.

## Open questions

- Exact registration API request/response shapes — see `contracts.md`.
- Whether registration and ingress live behind the same Spring Boot application from day one, or ingress is stubbed first — see `plan.md`.
