> 이 문서는 [`overview.md`](overview.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Product Overview

## Product

RelayHub (Java)는 event-driven data integration platform입니다. Source system으로부터 data-change event를 수신하여 내부적으로 canonical event model로 표준화한 뒤, 각 Target의 기존 API contract 형태로 Target system에 전달합니다 — retry, replay, audit, observability를 포함합니다.

## Core principle

Source와 Target system은 RelayHub 전용 payload contract를 채택하도록 절대 요구받지 않습니다. 각각은 기존의 payload 형태를 유지하며, RelayHub가 그 사이에서 해석, 표준화, 변환을 수행합니다.

## Problem

- system 간 point-to-point direct API call은 tight coupling을 만듭니다.
- 작은 변경도 targeted update 대신 전체 `DELETE`+`INSERT`나 전체 `UPSERT`를 촉발합니다.
- 실패한 delivery에는 retry, hook, alert가 없어서 failure state가 오랫동안 눈에 띄지 않습니다.
- integration logic이 system pair마다 중복되어 운영 복잡도가 증가합니다.
- delivery history와 replay mechanism의 부재로 수동 database correction이 강요됩니다.
- Source와 Target system이 서로의 API 구조에 tightly coupling됩니다.

RelayHub는 각 system pair가 이 복잡성을 개별적으로 해결하도록 두는 대신 중앙에서 흡수합니다.

## Product thesis

RelayHub는 Source system으로부터 data-change event를 수신하여 내부적으로 표준화하고, 각 Target의 기존 API contract에 맞게 변환한 뒤, retry, replay, audit, observability를 포함하여 신뢰성 있게 전달합니다.

## Users

- **Source system owners/developers** — data-change event를 발생시키는 system입니다. 기존 payload로 per-event Ingress URL을 호출하며, RelayHub envelope을 채택하도록 요구받지 않습니다.
- **Target system owners** — 자신의 기존 API contract를 통해 변환된 event를 수신하는 system입니다.
- **Operators** — Source/Source Event/Target/Subscription definition을 등록하고, delivery history를 모니터링하며, delivery가 dead-letter queue에 도달했을 때 수동 replay를 트리거하는 사람들입니다.

## V1 experience (first vertical slice)

1. `demo-source`가 생성된 Ingress URL로 `customer-created` payload를 전송합니다.
2. RelayHub는 URL의 registration으로부터 Source/Event를 식별하고, JSONPath extraction rule을 사용해 payload를 canonical event로 표준화한 뒤 저장합니다.
3. 두 개의 Subscription(Target A, Target B)이 각각 event를 mapping하고 전달합니다.
4. Target A는 성공하고; Target B는 (scenario를 위해 의도적으로) 실패합니다.
5. Target B는 재시도된 뒤 dead-letter queue(DLQ)로 이동합니다.
6. Operator가 Replay를 트리거하고; Target B가 성공합니다.
7. Event와 모든 Delivery Attempt가 end-to-end로 queryable합니다.

이것이 acceptance criteria에 어떻게 매핑되는지는 `docs/product/goals.md`("Success criteria")를 참고하고, 전체 specification은 `specs/001-push-event-delivery/`를 참고하세요.
