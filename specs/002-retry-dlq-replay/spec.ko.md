> 이 문서는 [`spec.md`](spec.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Spec 002: Retry, DLQ, Replay, Idempotency (without Kafka/Outbox yet)

## Summary

Spec 001의 single-attempt synchronous delivery를 retry 가능하고, 관측 가능하며, 복구 가능한 형태로
강화한다: 실패한 delivery는 backoff와 함께 재시도되고, 소진된 delivery는 조용히 유실되는 대신
dead-letter 상태(DLQ)로 이동하며, Operator는 죽은 delivery를 수동으로 replay할 수 있고, Source가
동일한 event를 (idempotency key 기준으로) 재전송해도 중복된 Canonical Event나 중복된 delivery가
생성되지 않는다.

Kafka와 Transactional Outbox 패턴은 Spec 003으로 **명시적으로 미룬다** — 왜 이것을
`docs/architecture/system-design.md`의 전체 Phase 2를 한 번의 변경으로 구축하는 대신 이렇게
분리했는지는 아래의 "Deliberately out of scope"와
`docs/decisions/ADR-0003-incremental-reliability-phase.md`를 참고한다.

## Goal

새로운 infrastructure(Kafka)를 도입하기 전에, failure를 관측 가능하고 복구 가능하게 만든다
(constitution: "Observable failure over hidden failure", "Replayable structure over manual
correction").

## In scope

- 하나의 (Event, Subscription) delivery 단위를 나타내는 `Delivery` entity. state는
  `PENDING`(현재 retry loop 내에서 아직 attempt가 가능한 상태) -> `SUCCEEDED` 또는 `DEAD`로 전이한다.
- Backoff를 적용한 retry는 **ingress request 내에서 synchronous하게** 실행된다 (아직 scheduler나
  queue는 없음 — 아래 "Deliberately out of scope" 참고): 고정된 MVP 정책으로 최대 3회 시도, 시도
  사이에 200ms/400ms backoff를 둔다. 모든 attempt는 여전히 (Spec 001의 모델대로) `DeliveryAttempt`
  row로 기록되며, 이제 자신의 parent `Delivery`와 연결된다.
- DLQ는 별도의 queue/table이 아니라 `Delivery`의 state(`DEAD`)로 표현한다 — "failure는 반드시
  visible하고 recoverable해야 한다"는 조건을 만족하는 가장 단순한 표현이다.
- `POST /api/deliveries/{deliveryId}/replay`: `DEAD` 상태의 delivery를 한 번 재시도한다; 성공하면
  `SUCCEEDED`로 이동하고 새 `DeliveryAttempt`를 추가한다; 실패하면 `DEAD` 상태를 유지하며 실패한
  attempt 역시 기록된다.
- `GET /api/deliveries?eventId=`는 raw attempt가 아니라 `Delivery` record를 반환하여, Operator가
  Target별 전체 상태를 한눈에 볼 수 있게 한다; attempt-level 상세 정보는
  `GET /api/deliveries/{deliveryId}/attempts`를 통해 계속 조회 가능하다.
- Ingress 단계에서의 idempotency 강제: 만약 Source Event에 `idempotencyKeyPath`/`idempotencyHeader`가
  설정되어 있고 동일한 `(sourceEventId, idempotencyKey)`에 대해 이미 Canonical Event가 존재한다면,
  Ingress endpoint는 (202가 아니라) 200으로 기존 Event를 반환하고 새 Delivery를 생성하지 **않는다**
  — 새 event로 취급되지 않는다.

## Deliberately out of scope (Spec 003+)

- Kafka 기반 internal delivery와 Transactional Outbox 패턴 — 여기서의 retry는 message broker나
  scheduled worker를 통하지 않고 in-process/synchronous하게 실행된다. 즉, retry 도중 JVM이 크래시
  났을 때 여전히 `PENDING`인 delivery는 자동으로 복구되지 않는다; `DEAD`(소진됨)와 terminal state만
  durable하고 안전하다. 이것이 바로 Spec 003의 Outbox/Kafka 파이프라인이 (`docs/architecture/system-design.md`
  기준으로) 메우려는 실제 gap이다.
- Subscription별 retry policy를 구성 가능하게 파싱하는 것(`Subscription.retryPolicy`는 이번 spec에서
  여전히 free-text/description 필드로 남으며, 모든 Subscription에 고정된 constant policy가 적용된다).
- ~~자동/스케줄된 replay — replay는 (`docs/architecture/system-design.md`의 MVP 정책 표
  "Recovery: Operator-triggered manual Replay"와 일치하게) operator가 트리거하는 경우
  (`POST .../replay`)에만 발생한다.~~ **대체됨(2026-09-12):** 이제 `DlqAutoReplayScheduler`가
  `relayhub.dlq.auto-replay-interval-ms`(기본 30초)마다 가장 오래된 `DEAD` delivery들을 자동으로
  재시도하며, Postgres advisory lock으로 레플리카 하나만 실행하도록 보장한다. `POST .../replay`는
  operator가 즉시 재시도를 강제할 때 여전히 존재하지만, 더 이상 유일한 replay 경로가 아니다.
- 프로세스 재시작 사이, 동시적인 중복 요청에 대한 idempotency (distributed lock 없음); uniqueness
  체크는 단순한 read-then-write이며, single-instance MVP에서는 허용 가능하다.

## Acceptance scenario

원래 design context(`docs/product/overview.md`의 "V1 experience", 4-7단계)에 있던 Target B failure
경로로 Spec 001의 시나리오를 확장한다:

1. Spec 001과 동일하게 `demo-source`/`customer-created`를 등록하고, 여기에 매 시도마다 실패하도록
   (HTTP 500을 반환하도록 stub된) 두 번째 Target `demo-target-b`와 Subscription을 추가한다.
2. Source payload를 Ingress URL에 POST한다. Target A는 성공한다; Target B의 Delivery는
   (backoff와 함께) 3번의 실패한 attempt를 거쳐 `DEAD` 상태가 된다.
3. `GET /api/deliveries?eventId=`는 하나의 `SUCCEEDED` Delivery(Target A)와 3개의 `DeliveryAttempt`가
   기록된 하나의 `DEAD` Delivery(Target B)를 보여준다.
4. Target B의 stub을 성공하도록 재구성한다. `POST /api/deliveries/{targetB-deliveryId}/replay`를 호출한다.
5. 이제 Target B의 Delivery는 `SUCCEEDED`이며, 4번째 `DeliveryAttempt`가 기록된다.
6. 동일한 idempotency key로 동일한 Source payload를 다시 POST한다. 응답은 기존 Event를 반영한다
   (새 Event도, 새 Delivery도 생성되지 않는다).
