# Goals

## V1 (MVP) goals

1. Deliver a thin, working end-to-end vertical slice before adding reliability or observability depth: register -> ingress -> JSONPath extraction -> canonical event -> mapping -> target HTTP delivery.
2. Never require a Source or Target system to adopt a RelayHub-specific payload envelope.
3. Make every delivery outcome — success, retry, DLQ, replay — observable and queryable, never silently lost.
4. Guarantee at-least-once delivery with idempotency support, without claiming exactly-once across the distributed pipeline.
5. Start as a modular monolith; add distributed complexity (Kafka, transactional outbox, multi-service split) only in the Reliability phase, once the vertical slice proves the flow works.
6. Demonstrate production-grade Java/Spring Boot backend engineering as a secondary, explicit project goal (see `docs/decisions/ADR-0002-java-spring-boot-stack.md`).

## Success criteria (bootstrap acceptance test)

Reused from Agent Development Starter's model: a new agent session, starting only from `CLAUDE.md` with no shared conversation link, must accurately answer:

- What is this project?
- Why does it exist?
- What are its core principles and architecture?
- What has been completed?
- What should happen next?

Every answer must be traceable to repository documentation and must not depend on design duplicated in the agent adapter.

## MVP acceptance scenario

The scenario in `docs/product/overview.md` ("V1 experience") — Source posts an event, RelayHub standardizes and routes it to two Subscriptions, one succeeds, one fails/retries/DLQs/replays successfully, and both the Event and all Delivery Attempts are queryable end-to-end — is the concrete Definition-of-Done target for the MVP. See `specs/001-push-event-delivery/verification.md`.

## Long-term direction

1. Phase 1 — Vertical Slice: register, ingress, JSONPath extraction, canonical event, mapping, synchronous-looking async HTTP delivery.
2. Phase 2 — Reliability: Kafka-based internal async delivery, transactional outbox, retry/backoff, DLQ, replay, idempotency.
3. Phase 3 — Observability / portfolio quality: Micrometer/Prometheus, OpenTelemetry, Grafana dashboards, a failure -> DLQ -> replay demo, architecture documentation, meaningful load/reliability tests.

Explicitly out of scope for V1 (see `docs/product/scope.md`): Scheduled Pull/Polling, CDC, an admin/visual mapping UI, multi-tenancy, complex BPM/workflow orchestration, and Kubernetes deployment. Do not build Scheduled Pull before the Push MVP is complete.
