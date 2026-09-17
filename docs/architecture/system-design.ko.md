> 이 문서는 [`system-design.md`](system-design.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# System Design

이 문서는 RelayHub 고유의 system boundary, domain module, data flow, delivery pipeline을 설명합니다. accepted RelayHub design context(2026-09-10)에서 직접 가져왔습니다(이 design이 전제하는 stack decision은 `docs/decisions/ADR-0002-java-spring-boot-stack.md` 참고).

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

External system은 RelayHub payload contract를 채택하도록 요구받지 않습니다 — RelayHub가 중간에서 해석하고, 표준화하고, 변환합니다.

## Registration model

| Entity | Required fields |
|---|---|
| Source | `key`, `name`, `description`, `authentication`, `status` |
| Source Event | `sourceId`, `key`, `name`, `description`, `resourceType`, `operation`, `ingressMethod`, `ingressPath`, `status` |
| Target | `key`, `name`, `description`, `baseUrl`, `authentication`, `status` |
| Subscription | `sourceEventId`, `targetId`, `name`, `description`, `targetMethod`, `targetPath`, `retryPolicy`, `status` |

operator가 code를 읽지 않고도 integration의 의도를 이해할 수 있도록 위의 모든 entity에는 `description`이 필수입니다.

### Source Event extraction rules

```text
resourceIdPath      JSONPath, e.g. $.customerNo
occurredAtPath      optional JSONPath
idempotencyKeyPath  optional JSONPath
idempotencyHeader   optional HTTP header name
payloadSchema       optional JSON Schema
```

RelayHub metadata(`eventType`, `operation`, `resourceType`, 또는 그 어떤 RelayHub 전용 envelope field)는 Source payload에 존재한다고 가정되지 않습니다 — registration이 선언한 것만 extract됩니다.

### Ingress URL convention

```text
POST   /ingress/v1/sap/customer-created
PATCH  /ingress/v1/sap/customer-patched
DELETE /ingress/v1/sap/customer-deleted
```

REST method는 data-change semantics로 매핑됩니다: `CREATED=POST`, `REPLACED=PUT`, `PATCHED=PATCH`, `DELETED=DELETE`. URL은 registration data로부터 생성되며, integration마다 하드코딩되지 않습니다.

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

이 형태는 RelayHub 내부용이며, Source나 Target에 강제되지 않습니다.

## Mapping strategy

| Step | Approach |
|---|---|
| Extract Source values | JSONPath |
| Generate Target payload | JSON Template / Mapping Definition |
| Preserve JSON types | Jackson `JsonNode` tree manipulation |
| Forbidden | Naive string `replace()`-based mapping |

예시 — Source payload `{"customerNo":"C10001","name":"ABC Dealer"}`를 Target template `{"dealerId":"${$.customerNo}","dealerName":"${$.name}","active":true}`로 매핑.

## Delivery and reliability (MVP policy)

| Aspect | MVP policy |
|---|---|
| Delivery guarantee | At-least-once |
| Delivery mode | Asynchronous HTTP to Target |
| Success criterion | HTTP 2xx |
| Failure handling | Retry + Backoff -> DLQ |
| Recovery | Operator-triggered manual Replay |
| Idempotency | 설정된 HTTP header 또는 JSONPath로부터 결정됨; 설정이 없으면 모든 request는 새로운 event로 취급됨; distributed pipeline 전체에 대한 exactly-once는 절대 주장하지 않음 |
| History | Event와 Delivery Attempt 모두 저장되고 queryable함 |

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

Transactional Outbox는 DB commit은 성공했지만 asynchronous event가 유실되는 gap을 방지합니다. `docs/decisions/ADR-0003-incremental-reliability-phase.md`에 따라 한 번에 만들지 않고 점진적으로 구축되었습니다: Spec 001은 synchronous delivery를 사용하는 thin vertical slice를 출시했고; Spec 002는 Retry/Backoff/DLQ/Replay/Idempotency를 추가했으며(여전히 synchronous); Spec 003은 synchronous delivery를 이 Outbox/Kafka pipeline으로 대체하면서 Spec 002의 `Delivery`/retry/DLQ logic을 변경 없이 재사용했습니다 — `specs/002-retry-dlq-replay/`와 `specs/003-kafka-outbox/` 참고.

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

premature한 microservice 분리는 없습니다. 분리는 distribution 필요성이 입증된 이후에만 정당화되며, 가정만으로는 안 됩니다.

## Technology stack

decision과 그 rationale은 `docs/decisions/ADR-0002-java-spring-boot-stack.md`를 참고하세요.

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

Kotlin은 나중에 작은 connector/extension에 선택적으로 채택될 수 있으며, core는 Java로 유지됩니다.

## Deployment boundary

Kubernetes 배포(이 service가 `cleanbrain.me`로 갈 경우/갈 때)는 이 repository의 범위 밖입니다 — `docs/architecture/overview.md`("Deployment boundary")와 `cleanbrain-me-infra`를 참고하세요.
