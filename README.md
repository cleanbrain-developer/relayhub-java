# RelayHub (Java)

Event-driven data integration platform. RelayHub receives data-change events from Source systems, standardizes them internally, and delivers them to Target systems in each Target's existing API contract — without requiring either side to adopt a RelayHub-specific payload envelope. It ships with retry, replay, audit, and observability built in.

This is the Java 21 / Spring Boot implementation. Other-language implementations of the same product design, if built, live in separate sibling repositories (see [`docs/decisions/ADR-0002-java-spring-boot-stack.md`](docs/decisions/ADR-0002-java-spring-boot-stack.md)).

## Status

The Reliability phase (register -> ingress -> canonical event -> mapping -> Retry/Backoff/DLQ/Replay/Idempotency, delivered via a Kafka + Transactional Outbox pipeline) is implemented end to end — see `docs/decisions/ADR-0003-incremental-reliability-phase.md` for why it was built as three incremental specs (001/002/003) instead of one change. Flyway migrations and delivery-task-level idempotency (protecting against Kafka redelivery) followed as ADR-0004. See [`docs/status/current-state.md`](docs/status/current-state.md) for exactly what exists, what's still a known gap, and what's next.

## Documentation map

| Topic | Path |
|---|---|
| Project identity | [`PROJECT.yaml`](PROJECT.yaml) |
| Product: problem, users, goals, scope | [`docs/product/`](docs/product/) |
| Architecture: structure, system design | [`docs/architecture/`](docs/architecture/) |
| Accepted decisions | [`docs/decisions/`](docs/decisions/) |
| Current phase and next work | [`docs/status/current-state.md`](docs/status/current-state.md) |
| Engineering/agent principles | [`.ai/constitution/`](.ai/constitution/) |
| Feature specs | [`specs/001-push-event-delivery/`](specs/001-push-event-delivery/), [`specs/002-retry-dlq-replay/`](specs/002-retry-dlq-replay/), [`specs/003-kafka-outbox/`](specs/003-kafka-outbox/) |

## Working with Claude Code

Start from [`CLAUDE.md`](CLAUDE.md) — it routes to the documents above in the order defined by [`docs/architecture/agent-context-model.md`](docs/architecture/agent-context-model.md). This README, `CLAUDE.md`, and the original design conversation are entry points only; the repository documents above are authoritative (see `docs/decisions/ADR-0001-repository-first-context.md`).

## Running locally

```bash
docker compose up -d      # Postgres + Kafka
./gradlew bootRun
```

Ingress is asynchronous: `POST /ingress/v1/...` returns as soon as the Event and its Outbox rows are durably stored, before any Target has been called. Poll `GET /api/deliveries?eventId=` to see delivery outcomes.

`./gradlew test` covers both the fast path (in-memory H2 + `@EmbeddedKafka`, no Docker needed) and `PostgresKafkaIntegrationTest` (Testcontainers, real Postgres + Kafka — **requires Docker**), which runs the same combined scenario against real infrastructure, applies `db/migration/V1__init_schema.sql` for real, and exercises the actual production `application.yml` settings (`ddl-auto: validate`), not the H2 test profile's.
