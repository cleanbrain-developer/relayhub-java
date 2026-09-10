# Spec 003: Verification

## Definition of done

- `./gradlew test` passes with `@EmbeddedKafka`-backed tests reproducing the Spec 001/002
  scenarios over the new async path (polling, not synchronous assertions).
- Manual verification against real Kafka + Postgres via `docker compose up -d` + `./gradlew bootRun`
  (the same rigor Spec 001/002 got), not just the embedded-broker test suite.
- Spec 002's endpoints (`GET /api/deliveries`, `GET /api/deliveries/{id}/attempts`,
  `POST /api/deliveries/{id}/replay`) behave the same as before from the API consumer's point of
  view — only the trigger path changed.
