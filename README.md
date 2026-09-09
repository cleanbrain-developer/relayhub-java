# RelayHub (Java)

Event-driven data integration platform. RelayHub receives data-change events from Source systems, standardizes them internally, and delivers them to Target systems in each Target's existing API contract — without requiring either side to adopt a RelayHub-specific payload envelope. It ships with retry, replay, audit, and observability built in.

This is the Java 21 / Spring Boot implementation. Other-language implementations of the same product design, if built, live in separate sibling repositories (see [`docs/decisions/ADR-0002-java-spring-boot-stack.md`](docs/decisions/ADR-0002-java-spring-boot-stack.md)).

## Status

Phase 1 — the first thin vertical slice (register -> ingress -> JSONPath extraction -> canonical event -> mapping -> target HTTP delivery) is implemented and passing its acceptance test. Retry/DLQ/Replay/Kafka are not built yet. See [`docs/status/current-state.md`](docs/status/current-state.md) for exactly what exists and what's next.

## Documentation map

| Topic | Path |
|---|---|
| Project identity | [`PROJECT.yaml`](PROJECT.yaml) |
| Product: problem, users, goals, scope | [`docs/product/`](docs/product/) |
| Architecture: structure, system design | [`docs/architecture/`](docs/architecture/) |
| Accepted decisions | [`docs/decisions/`](docs/decisions/) |
| Current phase and next work | [`docs/status/current-state.md`](docs/status/current-state.md) |
| Engineering/agent principles | [`.ai/constitution/`](.ai/constitution/) |
| First feature spec | [`specs/001-push-event-delivery/`](specs/001-push-event-delivery/) |

## Working with Claude Code

Start from [`CLAUDE.md`](CLAUDE.md) — it routes to the documents above in the order defined by [`docs/architecture/agent-context-model.md`](docs/architecture/agent-context-model.md). This README, `CLAUDE.md`, and the original design conversation are entry points only; the repository documents above are authoritative (see `docs/decisions/ADR-0001-repository-first-context.md`).

## Running locally

```bash
docker compose up -d      # Postgres only for now
./gradlew bootRun
```

`docker compose up -d` has not been verified in this environment (no Docker daemon available at implementation time) — see `docs/status/current-state.md` ("Known constraints"). `./gradlew test` runs against an in-memory H2 database and does not require Docker.
