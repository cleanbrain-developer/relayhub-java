> 이 문서는 [`spec.md`](spec.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Spec 001: Push Event Delivery (First Vertical Slice)

## Summary

RelayHub의 첫 번째 얇은 end-to-end vertical slice: Source/Source Event/Target/Subscription를 등록하고, 자동 생성된 Ingress URL로 push event를 수신하여, 이를 추출하고 표준화해 Canonical Event로 만들고, Subscription에 따라 매핑한 뒤 각 Target에 HTTP로 전달한다.

## Goal

신뢰성 관련 깊이(Kafka, Transactional Outbox, retry/backoff, DLQ, replay — Spec 002+, `docs/product/goals.md`의 "Reliability phase" 참고)를 추가하기 전에 전체 경로가 동작함을 증명한다.

## In scope

- Source / Source Event / Target / Subscription 등록 API(생성, 조회).
- 등록 데이터로부터 생성되는(하드코딩이 아닌) Source Event별 Ingress URL.
- 해당 URL로 임의의 Source JSON payload를 수신.
- `resourceIdPath`(필수)에 대한 JSONPath 추출과, 설정된 경우 `occurredAtPath` / `idempotencyKeyPath` / `idempotencyHeader`도 함께 추출.
- Source payload에 대한 선택적 JSON Schema validation.
- Canonical Event 구성 및 영속화(스키마는 `docs/architecture/system-design.md`에 있음).
- 트리거된 Source Event에 대한 Subscription 조회.
- 문자열 `replace()`가 아니라 Jackson `JsonNode` tree manipulation으로 구현하는, Subscription별 JSON template 기반 Target payload 매핑.
- 각 Target에 대한 동기적으로 보이는(아직 Kafka 기반이 아닌, in-process) 비동기 HTTP delivery.
- delivery 결과(성공/실패)를 Delivery Attempt로 영속화.

## Out of scope (이후 spec으로 이연)

- Kafka 기반 내부 delivery와 Transactional Outbox pattern.
- Retry/backoff, DLQ, 수동 Replay.
- Idempotency deduplication 로직(해당 필드는 Canonical Event에 기록되지만 아직 dedup enforcement는 없음).
- 구조화된 metrics/tracing dashboard(기본 구조화 로그만 제공).
- 로컬에서 acceptance scenario를 실행하는 데 필요한 수준을 넘어서는, 등록 또는 Ingress API에 대한 인증/인가.

## Acceptance scenario

전체 서사는 `docs/product/overview.md`("V1 experience")를 참고. 이 spec에 한정해 구체적으로는(두 번째 Target에 대한 Retry/DLQ/Replay는 Spec 002+이므로, 단일 Target 버전):

1. `demo-source`(Source), `customer-created`(Source Event: `resourceType=customer`, `operation=CREATED`, `ingressMethod=POST`, `resourceIdPath=$.customerNo`), `demo-target-a`(Target), 그리고 이들을 연결하는 Subscription을 등록한다.
2. 생성된 Ingress URL로 `{"customerNo":"C10001","name":"ABC Dealer","email":"dealer@example.com"}`를 POST한다.
3. RelayHub는 URL로부터 Source/Event를 식별하고, JSONPath를 통해 `resourceId=C10001`을 추출하며, Canonical Event를 만들어 저장한다.
4. RelayHub는 Subscription을 조회하고, 그 template에 따라 payload를 매핑한 뒤 `demo-target-a`의 HTTP endpoint를 호출한다.
5. Delivery Attempt(성공, HTTP 2xx)가 저장되고 Event와 함께 조회 가능한 상태가 된다.

## Open questions

- 등록 API의 정확한 request/response 형태 — `contracts.md` 참고.
- 등록과 ingress가 처음부터 동일한 Spring Boot application 안에 있을지, 아니면 ingress를 먼저 stub으로 둘지 — `plan.md` 참고.
