> 이 문서는 [`scope.md`](scope.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Scope

## V1 (MVP) in scope

- Source / Source Event / Target / Subscription registration API
- 자동 생성된, per-Source-Event Ingress URL (예: `POST /ingress/v1/sap/customer-created`)
- 임의의 Source JSON payload 수신, 선택적인 JSON Schema validation 포함
- JSONPath 기반 metadata extraction (`resourceIdPath`, `occurredAtPath`, `idempotencyKeyPath`/`idempotencyHeader`)
- Canonical Event 생성 및 persistence (RelayHub 내부 model만 해당하며, Source/Target에 강제되지 않음)
- Subscription 기반 routing과 Source -> Target JSON payload mapping (JSON Template / Mapping Definition, string `replace()`가 아닌 Jackson `JsonNode` tree manipulation으로 구현)
- Target으로의 asynchronous HTTP delivery, Retry/Backoff, DLQ, 수동 Replay, Idempotency 포함
- end-to-end로 queryable한 Event history와 Delivery Attempt history
- Structured logging과 Metrics/Tracing foundation
- Docker Compose 기반의 reproducible local environment

## V1 (MVP) out of scope

- Scheduled Pull / Polling (Push MVP가 완료될 때까지 보류)
- CDC (change data capture)
- Admin web UI / visual mapping UI
- Multi-tenancy
- 복잡한 BPM / workflow orchestration
- Kubernetes 배포 (V1은 Docker Compose만 사용; production K8s 배포가 이루어진다면 그것은 이 repository가 아니라 `cleanbrain-me-infra`의 책임입니다 — `docs/architecture/overview.md` 참고)
- Source system을 위한 external Kafka contract (V1에서 Kafka는 internal-only)

## Scope rule

미래의 확장을 막지 않을 정도로만 out-of-scope 항목을 고려하세요(예: registration model에는 이미 나중에 Scheduled Pull을 지원할 수 있는 field가 있습니다). V1에서 이를 위한 placeholder module, speculative abstraction, 사용되지 않는 script를 추가하지 마세요.

## Open scope decisions

- 이 service가 `cleanbrain-me-infra`에서 production Kubernetes 배포를 받을지/언제 받을지, 그리고 어떤 hostname/namespace 아래에서 받을지는 아직 결정되지 않았습니다 — V1은 local Docker Compose만을 target으로 합니다.
- 다국어 RelayHub variant(예: Node/NestJS implementation)는 설계상 이 repository의 scope 항목이 아니라 별도의 sibling project입니다. `docs/decisions/ADR-0002-java-spring-boot-stack.md`를 참고하세요.
