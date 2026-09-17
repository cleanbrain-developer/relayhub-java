> 이 문서는 [`verification.md`](verification.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Spec 002: Verification

## Definition of done

- `./gradlew test`가, 새로운 Target-B DLQ/replay 및 idempotency 테스트를 포함해 통과한다 (Spec 001과
  동일하게 H2 기반 — 실제 Postgres 재검증은 Spec 003/Next에서 Testcontainers 기반 Postgres 테스트가
  추가되기 전까지 Spec 001과 동일한 수동 프로세스를 따른다, `docs/status/current-state.md` 참고).
- Spec 001의 시나리오는 변경 없이 그대로 통과한다 (single-attempt-success 경로에 regression이 없다).
- `GET /api/deliveries?eventId=`와 `GET /api/deliveries/{deliveryId}/attempts`는 단지 컴파일된다고
  믿는 것이 아니라, 둘 다 테스트로 실제 검증된다.
