> 이 문서는 [`spec.md`](spec.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Spec 003: Kafka-Backed Delivery via Transactional Outbox

## Summary

Spec 002의 in-process, synchronous retry loop(ingress request 내부에서 실행되며 retry 도중 크래시가
나면 durable하지 않다 — ADR-0003 참고)를, `docs/architecture/system-design.md`가 원래 의도했던
durable 파이프라인으로 대체한다:

```text
Ingress Transaction
    -> Event stored
    -> Outbox stored in the same transaction
    -> Outbox Publisher
    -> Kafka
    -> Delivery Worker
    -> Target API
```

Spec 002의 `Delivery`/`DeliveryAttempt`/retry/DLQ/Replay 모델은 **대체되는 것이 아니라 재사용된다**
— 변경되는 것은 오직 `DeliveryService.deliver(...)`를 *무엇이 트리거하는가*뿐이다. "ingress 도중
inline으로 호출됨"에서 "Outbox Publisher가 메시지를 건넨 후 Kafka consumer가 호출함"으로 바뀐다.

## Goal

ADR-0003이 지적한 durability gap을 메운다: delivery task는 ingress HTTP request가 전체 retry loop
동안 살아있는 것에 의존하지 않고, "Event committed"와 "Target actually called" 사이의 크래시에서도
살아남아야 한다.

## In scope

- `OutboxEvent`: (Event, Subscription) delivery task당 하나의 row이며, ingress 중 Canonical Event와
  **같은 database transaction** 안에서 기록된다. 이것이 실제 transactional 보장이다 — Event가
  commit되면, Kafka 유무와 상관없이 그 delivery task가 disk에 존재함이 보장된다.
- `OutboxPublisher`: publish되지 않은 `OutboxEvent` row를 읽어 `relayhub.delivery-tasks` topic에
  row당 하나의 Kafka message를 publish하고, row를 published로 표시하는 scheduled poller
  (`@Scheduled`). Publish 실패 시 row는 다음 poll을 위해 `PENDING` 상태로 남는다 (at-least-once).
- `DeliveryWorker`: `relayhub.delivery-tasks`에 대한 `@KafkaListener`로, ID로 Event와 Subscription을
  로드하여 **기존의** `DeliveryService.deliver(...)`를 호출한다 — Spec 002의 retry/backoff/DLQ
  로직은 변경 없이 그대로 실행되며, 다만 ingress request thread 대신 Kafka consumer thread에서
  실행된다.
- `IngressService.handle(...)`는 더 이상 `DeliveryService.deliver(...)`를 직접 호출하지 않는다.
  대신 Event와 같은 transaction 안에서 활성 Subscription마다 하나의 `OutboxEvent`를 기록한다.
- `docker-compose.yml`에 single-node KRaft Kafka service(Zookeeper 없음)가 추가된다.
- 테스트는 Spring Kafka의 `@EmbeddedKafka`(in-process broker, `./gradlew test`에 Docker 불필요)를
  사용하며, 이제 비동기가 된 delivery outcome에 대해 Awaitility 스타일 polling을 사용한다.

## Deliberately out of scope

- Spec 002가 이미 가진 것 이상의 consumer-side idempotent processing (예: worker 크래시 후 Kafka
  redelivery로 인해 delivery task가 두 번 소비되면, 동일한 (Event, Subscription) 쌍에 대해 두 번째
  `Delivery` row가 생성된다; Spec 002의 idempotency dedup은 delivery-task 레벨이 아니라 *ingress*
  레벨에서 `(sourceEventId, idempotencyKey)`를 기준으로 이루어진다). 이것은 여기서 조용히 해결된 것이
  아니라 알려진 gap으로 남겨둔다 — 이 spec 이후 `docs/status/current-state.md`의
  "Known constraints" 참고.
- 단일 partition 이상의 Kafka topic partitioning/ordering 보장; worker를 하나의 instance 이상으로
  확장하는 것은 시도하지 않는다.
- Outbox row cleanup/archival — published된 row는 계속 쌓인다. local MVP에서는 허용 가능하며,
  이것이 언젠가 long-lived 환경에서 운영된다면 그때 재검토한다.
- Exactly-once Kafka semantics (transactional producer). At-least-once가 명시된 MVP 정책이며
  (`docs/architecture/system-design.md`), Spec 002의 idempotency dedup이 가장 흔히 문제가 되는
  ingress-level 중복 케이스에 대해 이미 존재한다.

## Acceptance scenario

Spec 001/002의 시나리오를 새로운 비동기 hop으로 확장한다:

1. Spec 002와 동일하게 Source/Source Event/Target A(항상 성공)/Target B(3회 실패)/Subscription을
   등록한다.
2. Ingress URL에 POST한다. 응답은 더 이상 delivery가 완료되었음을 암시하지 않는다 — Event와 그
   Outbox row가 durable하게 저장되었음을 확인해줄 뿐이다 (여전히 HTTP 202).
3. 두 Delivery가 모두 terminal state에 도달할 때까지 대기(poll)한다: Target A는 `SUCCEEDED`,
   Target B는 3회 시도 후 `DEAD` — 이제 ingress request 자체가 아니라 `OutboxPublisher` -> Kafka
   -> `DeliveryWorker`에 의해 만들어진다.
4. 기존의 `POST /api/deliveries/{id}/replay` endpoint(Spec 002와 동일하게 synchronous)를 통해
   Target B의 `DEAD` delivery를 replay한다 — 새로운 파이프라인 위에서도 Spec 002의 replay 경로가
   변경 없이 여전히 동작함을 확인한다.
5. 동일한 idempotency key를 가진 중복 ingress는 (Spec 002의 보장대로) 여전히 ingress에서 dedup되며,
   따라서 새로운 Outbox row도 생성하지 않는다.
