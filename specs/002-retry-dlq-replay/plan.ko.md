> 이 문서는 [`plan.md`](plan.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Spec 002: Implementation Plan

**Branch**: `002-retry-dlq-replay` | **Date**: 2026-09-10 | **Spec**: [link to ../spec.md](spec.md)

**Input**: 기록되지 않음 — 이 spec은 Spec Kit 도입 이전에 작성됨

1. `delivery.DeliveryState` enum (`PENDING`, `SUCCEEDED`, `DEAD`)과 `delivery.Delivery` entity
   (`eventId`, `subscriptionId`, `targetId`, `state`, `attemptCount`, timestamps) 및 repository를 추가한다.
2. `DeliveryAttempt`에 `deliveryId` FK를 추가한다; 기존의 attempt별 필드는 그대로 유지한다.
3. `DeliveryService.deliver(...)`를 다음과 같이 재작성한다: `Delivery` row를 생성하고, bounded retry loop
   (3회 시도, 200ms/400ms backoff)를 실행하여 attempt마다 기존의 mapping/HTTP-call 로직을 호출하며,
   시도마다 `DeliveryAttempt`를 기록하고, 최종적으로 `Delivery`의 state를 설정한다.
4. `DeliveryService.replay(deliveryId)`를 추가한다: `DEAD` 상태의 `Delivery`를 로드하여 한 번 더
   attempt를 실행하고, 그 결과에 따라 state를 갱신한다.
5. `DeliveryController`: `GET /api/deliveries?eventId=`가 `Delivery` summary를 반환하도록 변경하고,
   attempt-level history를 위한 `GET /api/deliveries/{deliveryId}/attempts`를 추가하며,
   `POST /api/deliveries/{deliveryId}/replay`를 추가한다.
6. `EventRepository.findBySourceEventIdAndIdempotencyKey(...)`를 추가하고, `IngressService.handle(...)`가
   idempotency key가 존재할 때 새 Event를 생성하기 전에 이를 확인하도록 한다.
7. Target-B failure -> retry -> DLQ -> replay 경로와, 중복 idempotency key를 가진 ingress request에
   대한 테스트를 integration test suite에 추가한다.
