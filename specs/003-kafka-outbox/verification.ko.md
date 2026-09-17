> 이 문서는 [`verification.md`](verification.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Spec 003: Verification

## Definition of done

- `./gradlew test`가 통과하며, `@EmbeddedKafka` 기반 테스트가 새로운 비동기 경로 위에서 Spec 001/002의
  시나리오를 (synchronous assertion이 아니라 polling으로) 재현한다.
- `docker compose up -d` + `./gradlew bootRun`을 통한 실제 Kafka + Postgres 대상 수동 검증
  (embedded-broker test suite만이 아니라, Spec 001/002와 동일한 수준의 엄밀함으로)이 이루어진다.
- Spec 002의 endpoint (`GET /api/deliveries`, `GET /api/deliveries/{id}/attempts`,
  `POST /api/deliveries/{id}/replay`)는 API consumer 관점에서 이전과 동일하게 동작한다 — 오직
  trigger 경로만 변경되었다.
