# ADR-0003: Split the Reliability Phase into Incremental Specs

- Status: Accepted
- Date: 2026-09-10
- Deciders: Project maintainer

## Context

`docs/architecture/system-design.md` (from the original design context) describes "Phase 2:
Reliability" as one bundled unit: Kafka-backed internal delivery, the Transactional Outbox
pattern, Retry/Backoff, DLQ, Replay, and Idempotency enforcement. Implementing all of it in one
change is a large, hard-to-review unit that mixes two different kinds of risk: (1) delivery
semantics (retry, failure visibility, recovery) that can be built and tested entirely within the
existing synchronous request flow, and (2) new infrastructure (Kafka, an Outbox table, a
publisher/consumer split) that changes the runtime topology and local Docker Compose setup.

The maintainer was asked to choose between building the full bundle at once or splitting it, and
chose to split it.

## Decision

Split the Reliability phase into at least two specs:

- **Spec 002** (`specs/002-retry-dlq-replay/`): `Delivery` state (`PENDING`/`SUCCEEDED`/`DEAD`),
  in-process Retry + Backoff, DLQ as a `Delivery` state, Operator-triggered Replay, and
  idempotency-key deduplication at ingress. No new infrastructure; delivery still happens
  synchronously within the ingress request, same as Spec 001.
- **Spec 003+** (not started): Kafka-backed internal delivery and the Transactional Outbox
  pattern, replacing Spec 002's in-process retry loop with the durable
  `Ingress Transaction -> Event -> Outbox -> Publisher -> Kafka -> Delivery Worker -> Target API`
  pipeline described in `docs/architecture/system-design.md`.

This does not change the target architecture in `system-design.md` — it only changes the order
and granularity in which it is built and verified. Spec 002's `Delivery` entity and states are
designed to be reused, not replaced, once Spec 003 moves attempt execution off the request thread
and onto a Kafka consumer.

## Consequences

### Positive

- Each spec is independently testable and revertible; Spec 002 shipped with a real integration
  test (intentional Target failure -> retry -> DLQ -> replay) before any new infrastructure was
  introduced.
- Failure-visibility and recovery — the properties the constitution weights above feature count —
  land first, before the higher-risk infrastructure change.

### Costs and risks

- Spec 002's retries are synchronous/in-process: a `PENDING` delivery is not durable across a JVM
  crash mid-retry (only `DEAD`/`SUCCEEDED` are safely persisted terminal states). This gap is
  explicitly Spec 003's reason to exist, not a defect to silently work around in Spec 002.
- `Subscription.retryPolicy` is not yet parsed into a real per-subscription policy in Spec 002; a
  fixed constant policy (3 attempts, 200ms/400ms backoff) applies to every Subscription. Revisit
  once real usage shows what should be configurable.

## Alternatives considered

### Build all of Phase 2 (Kafka + Outbox + Retry + DLQ + Replay + Idempotency) in one change

This is what `system-design.md` originally described as one phase. Rejected as the immediate next
step because of its size and because it couples an infrastructure change to delivery-semantics
changes that don't actually depend on each other.
