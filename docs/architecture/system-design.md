# System Design

This document describes RelayHub's own system boundaries, domain modules, data flow, and delivery pipeline. Sourced directly from the accepted RelayHub design context (2026-09-10, see `docs/decisions/ADR-0002-java-spring-boot-stack.md` for the stack decision this design assumes).

## Core data flow

```text
Source system (existing payload)
    -> per-Source-Event Ingress URL
    -> registration-based Source/Event/Operation identification
    -> JSONPath extraction (resourceId, occurredAt, idempotencyKey, schema validation)
    -> Canonical Event created and stored (RelayHub-internal model only)
    -> Subscription lookup
    -> per-Target payload mapping
    -> asynchronous Target HTTP delivery
    -> success: Audit
       failure: Retry + Backoff -> DLQ -> Operator Replay
```

External systems are never required to adopt a RelayHub payload contract — RelayHub interprets, standardizes, and transforms in the middle.

## Registration model

| Entity | Required fields |
|---|---|
| Source | `key`, `name`, `description`, `authentication`, `status` |
| Source Event | `sourceId`, `key`, `name`, `description`, `resourceType`, `operation`, `ingressMethod`, `ingressPath`, `status` |
| Target | `key`, `name`, `description`, `baseUrl`, `authentication`, `status` |
| Subscription | `sourceEventId`, `targetId`, `name`, `description`, `targetMethod`, `targetPath`, `retryPolicy`, `status` |

`description` is required on every entity above so an operator can understand integration intent without reading code.

### Source Event extraction rules

```text
resourceIdPath      JSONPath, e.g. $.customerNo
occurredAtPath      optional JSONPath
idempotencyKeyPath  optional JSONPath
idempotencyHeader   optional HTTP header name
payloadSchema       optional JSON Schema
```

RelayHub metadata (`eventType`, `operation`, `resourceType`, or any RelayHub-specific envelope field) is never assumed to exist in the Source payload — only what the registration declares is extracted.

### Ingress URL convention

```text
POST   /ingress/v1/sap/customer-created
PATCH  /ingress/v1/sap/customer-patched
DELETE /ingress/v1/sap/customer-deleted
```

REST method maps to data-change semantics: `CREATED=POST`, `REPLACED=PUT`, `PATCHED=PATCH`, `DELETED=DELETE`. URLs are generated from registration data, never hardcoded per integration.

## Canonical Event (internal only)

```json
{
  "eventId": "relay-generated-id",
  "sourceId": "...",
  "sourceEventId": "...",
  "resourceType": "customer",
  "resourceId": "C10001",
  "operation": "PATCHED",
  "occurredAt": "2026-09-10T00:00:00Z",
  "receivedAt": "2026-09-10T00:00:01Z",
  "idempotencyKey": "optional-source-key",
  "payload": { "customerNo": "C10001", "name": "ABC Dealer" }
}
```

This shape is RelayHub-internal and is never imposed on a Source or Target.

## Mapping strategy

| Step | Approach |
|---|---|
| Extract Source values | JSONPath |
| Generate Target payload | JSON Template / Mapping Definition |
| Preserve JSON types | Jackson `JsonNode` tree manipulation |
| Forbidden | Naive string `replace()`-based mapping |

Example — Source payload `{"customerNo":"C10001","name":"ABC Dealer"}` mapped via Target template `{"dealerId":"${$.customerNo}","dealerName":"${$.name}","active":true}`.

## Delivery and reliability (MVP policy)

| Aspect | MVP policy |
|---|---|
| Delivery guarantee | At-least-once |
| Delivery mode | Asynchronous HTTP to Target |
| Success criterion | HTTP 2xx |
| Failure handling | Retry + Backoff -> DLQ |
| Recovery | Operator-triggered manual Replay |
| Idempotency | From a configured HTTP header or JSONPath; absent config means every request is treated as a new event; exactly-once across the distributed pipeline is never claimed |
| History | Both Event and Delivery Attempt are stored and queryable |

### Target reliability structure (implemented — Specs 002/003)

```text
Ingress Transaction
    -> Event stored
    -> Outbox stored in the same transaction
    -> Outbox Publisher
    -> Kafka
    -> Delivery Worker
    -> Target API
```

Transactional Outbox avoids the gap where a DB commit succeeds but the asynchronous event is lost. Built incrementally rather than all at once, per `docs/decisions/ADR-0003-incremental-reliability-phase.md`: Spec 001 shipped the thin vertical slice with synchronous delivery; Spec 002 added Retry/Backoff/DLQ/Replay/Idempotency (still synchronous); Spec 003 replaced synchronous delivery with this Outbox/Kafka pipeline, reusing Spec 002's `Delivery`/retry/DLQ logic unchanged — see `specs/002-retry-dlq-replay/` and `specs/003-kafka-outbox/`.

## Recommended domain modules (Modular Monolith)

```text
source
sourceevent
target
subscription
ingress
event
delivery
mapping
outbox
replay
observability
common
```

No premature microservice split. Splitting is only justified once distribution needs are proven, not assumed.

## Technology stack

See `docs/decisions/ADR-0002-java-spring-boot-stack.md` for the decision and its rationale.

```text
Language / Framework   Java 21 / Spring Boot 3.x
Build                  Gradle Kotlin DSL
Data                   PostgreSQL / Redis
Async                  Kafka (internal only; no external Kafka contract for Source systems)
Reliability            Resilience4j / Transactional Outbox
Mapping                Jackson / JSONPath
Contract               OpenAPI / JSON Schema
Test                   JUnit 5 / Testcontainers
Observability          Micrometer / OpenTelemetry / Prometheus / Grafana
Local runtime          Docker Compose
```

Kotlin may be selectively adopted later for small connectors/extensions; the core stays Java.

## Deployment boundary

Kubernetes deployment (if/when this service goes to `cleanbrain.me`) is out of scope for this repository — see `docs/architecture/overview.md` ("Deployment boundary") and `cleanbrain-me-infra`.
