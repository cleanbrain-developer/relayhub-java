> 이 문서는 [`goals.md`](goals.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Goals

## V1 (MVP) goals

1. reliability나 observability의 깊이를 더하기 전에, thin하고 end-to-end로 동작하는 vertical slice를 먼저 전달합니다: register -> ingress -> JSONPath extraction -> canonical event -> mapping -> target HTTP delivery.
2. Source나 Target system이 RelayHub 전용 payload envelope을 채택하도록 절대 요구하지 않습니다.
3. 모든 delivery outcome — success, retry, DLQ, replay — 을 observable하고 queryable하게 만들며, 조용히 유실되지 않도록 합니다.
4. distributed pipeline 전체에 대한 exactly-once를 주장하지 않으면서, idempotency를 지원하는 at-least-once delivery를 보장합니다.
5. modular monolith로 시작하고, vertical slice가 flow가 동작함을 입증한 이후 Reliability phase에서만 distributed complexity(Kafka, transactional outbox, multi-service split)를 추가합니다.
6. secondary하고 명시적인 project goal로서 production-grade Java/Spring Boot backend engineering을 증명합니다(`docs/decisions/ADR-0002-java-spring-boot-stack.md` 참고).

## Success criteria (bootstrap acceptance test)

Agent Development Starter의 model에서 재사용됨: 공유된 conversation link 없이 오직 `AGENTS.md`에서만 시작하는 새로운 agent session은 다음 질문에 정확히 답할 수 있어야 합니다:

- 이 project는 무엇인가?
- 왜 존재하는가?
- 핵심 principle과 architecture는 무엇인가?
- 무엇이 완료되었는가?
- 다음에 무엇을 해야 하는가?

모든 답변은 repository documentation으로 추적 가능해야 하며, agent adapter에 중복된 design에 의존해서는 안 됩니다.

## MVP acceptance scenario

`docs/product/overview.md`("V1 experience")의 scenario — Source가 event를 post하고, RelayHub가 그것을 표준화하여 두 개의 Subscription으로 routing하며, 하나는 성공하고 다른 하나는 실패/retry/DLQ를 거쳐 성공적으로 replay되고, Event와 모든 Delivery Attempt가 end-to-end로 queryable한 것 — 이 MVP의 구체적인 Definition-of-Done target입니다. `specs/001-push-event-delivery/verification.md`를 참고하세요.

## Long-term direction

1. Phase 1 — Vertical Slice: register, ingress, JSONPath extraction, canonical event, mapping, synchronous하게 보이는 async HTTP delivery.
2. Phase 2 — Reliability: Kafka 기반 internal async delivery, transactional outbox, retry/backoff, DLQ, replay, idempotency.
3. Phase 3 — Observability / portfolio quality: Micrometer/Prometheus, OpenTelemetry, Grafana dashboard, failure -> DLQ -> replay demo, architecture documentation, 의미 있는 load/reliability test.

V1에서 명시적으로 범위 밖인 것(`docs/product/scope.md` 참고): Scheduled Pull/Polling, CDC, admin/visual mapping UI, multi-tenancy, 복잡한 BPM/workflow orchestration, Kubernetes 배포. Push MVP가 완료되기 전에 Scheduled Pull을 만들지 마세요.
