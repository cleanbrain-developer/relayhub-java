# Scope

## V1 (MVP) in scope

- Source / Source Event / Target / Subscription registration APIs
- Auto-generated, per-Source-Event Ingress URL (e.g. `POST /ingress/v1/sap/customer-created`)
- Receiving arbitrary Source JSON payloads, with optional JSON Schema validation
- JSONPath-based metadata extraction (`resourceIdPath`, `occurredAtPath`, `idempotencyKeyPath`/`idempotencyHeader`)
- Canonical Event creation and persistence (RelayHub-internal model only, never imposed on Source/Target)
- Subscription-based routing and Source -> Target JSON payload mapping (JSON Template / Mapping Definition, implemented via Jackson `JsonNode` tree manipulation — not string `replace()`)
- Asynchronous HTTP delivery to Targets, with Retry/Backoff, DLQ, manual Replay, and Idempotency
- Event history and Delivery Attempt history, queryable end-to-end
- Structured logging and a Metrics/Tracing foundation
- Docker Compose based, reproducible local environment

## V1 (MVP) out of scope

- Scheduled Pull / Polling (deferred until the Push MVP is complete)
- CDC (change data capture)
- Admin web UI / visual mapping UI
- Multi-tenancy
- Complex BPM / workflow orchestration
- Kubernetes deployment (Docker Compose only for V1; production K8s deployment, if it happens, is `cleanbrain-me-infra`'s responsibility, not this repository's — see `docs/architecture/overview.md`)
- An external Kafka contract for Source systems (Kafka is internal-only in V1)

## Scope rule

Consider out-of-scope items only enough to avoid blocking future extension (e.g. the registration model already has fields that would support Scheduled Pull later). Do not add placeholder modules, speculative abstractions, or unused scripts for them in V1.

## Open scope decisions

- Whether/when this service gets a production Kubernetes deployment in `cleanbrain-me-infra`, and under what hostname/namespace, is not yet decided — V1 targets local Docker Compose only.
- Multi-language RelayHub variants (e.g. a Node/NestJS implementation) are a separate, sibling project by design, not a scope item of this repository. See `docs/decisions/ADR-0002-java-spring-boot-stack.md`.
