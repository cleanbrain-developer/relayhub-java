# Spec 002: Verification

## Definition of done

- `./gradlew test` passes, including the new Target-B DLQ/replay and idempotency tests (H2-backed,
  same as Spec 001 — real Postgres re-verification follows the same manual process as Spec 001
  until Spec 003/Next adds a Testcontainers-based Postgres test, see `docs/status/current-state.md`).
- The Spec 001 scenario still passes unmodified (no regression in the single-attempt-success path).
- `GET /api/deliveries?eventId=` and `GET /api/deliveries/{deliveryId}/attempts` are both exercised
  by a test, not just believed to compile.
