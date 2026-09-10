# Current State

Last updated: 2026-09-10

## Current phase

`phase-2a-retry-dlq-replay` — the Reliability phase is being built incrementally (ADR-0003, chosen over building Kafka+Outbox+Retry+DLQ+Replay+Idempotency in one change). Spec 002 (in-process Retry/Backoff, DLQ, Operator Replay, idempotency dedup) is implemented and passing its acceptance test. Spec 003 (Kafka-backed delivery, Transactional Outbox) has not started. `PROJECT.yaml` reflects this (`current_phase.id: phase-2a-retry-dlq-replay`).

## Completed

- Captured RelayHub's product/architecture design from the accepted design context (2026-09-10) and persisted it into `docs/product/`, `docs/architecture/system-design.md`, and `docs/decisions/`.
- Adopted the Agent Development Starter foundation (`PROJECT.yaml`, `CLAUDE.md`, `.ai/constitution/`, `docs/architecture/agent-context-model.md`, `docs/architecture/repository-structure.md`, ADR-0001).
- Decided and recorded the Java 21/Spring Boot core stack decision (ADR-0002), including the `relayhub-<lang>` naming convention for future sibling repositories.
- **Spec 001** (`specs/001-push-event-delivery/`): Gradle/Spring Boot 3.3.4 (Java 21 toolchain) scaffold with the `source`, `sourceevent`, `target`, `subscription`, `event`, `mapping`, `delivery`, `ingress`, `common` modules; registration REST APIs; auto-generated per-Source-Event Ingress URL with REST-semantics method mapping; JSONPath extraction; optional JSON Schema validation; Canonical Event persistence; `JsonNode`-tree payload mapping; HTTP delivery to Targets. Verified via `./gradlew test` (`IngressVerticalSliceTest`, WireMock + H2) and manually against real Postgres via `docker compose up -d` + `./gradlew bootRun` + curl.
- **Spec 002** (`specs/002-retry-dlq-replay/`, ADR-0003): a `Delivery` entity (`PENDING`/`SUCCEEDED`/`DEAD`) wrapping the existing `DeliveryAttempt` history; in-process Retry + Backoff (3 attempts, 200ms/400ms); DLQ as the `DEAD` state; `POST /api/deliveries/{id}/replay` for Operator-triggered recovery; `GET /api/deliveries?eventId=` (Delivery-level) and `GET /api/deliveries/{id}/attempts` (attempt-level); idempotency-key deduplication at ingress (`GET`/`POST` return the existing Event with `deduplicated: true`, HTTP 200 instead of 202, and create no new Deliveries). Verified via `./gradlew test` (`DlqReplayIdempotencyTest`: Target B fails 3x -> DEAD -> replay -> SUCCEEDED, plus duplicate-idempotency-key ingress) **and** manually against real Postgres (2026-09-10): same scenario replayed via curl, including confirming `created_at`/`updated_at` are both populated after a fix (see item 5 below).
- Five implementation-driven corrections to `docs/architecture/system-design.md`'s assumptions, worth knowing for later specs: (1) Spring Framework 6's `HttpMethod` is no longer a plain enum, so it cannot be JPA-`@Enumerated`-mapped directly — introduced `common.HttpVerb`; (2) `key` is a reserved word in H2's grammar, so `spring.jpa.properties.hibernate.globally_quoted_identifiers: true` is set; (3) `@Lob String` on Postgres maps to an OID-based large object that fails to read back outside its creating transaction — every large-text field uses `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` instead; (4) Spring's `@SpringBootTest` context caching shares the same H2 in-memory database across test classes with identical configuration — test classes must use disjoint Source/Target `key`s to avoid cross-test "key already registered" failures; (5) against real Postgres, `Delivery.createdAt` (`@CreationTimestamp`) came back `null` in the DB while `updatedAt` (`@UpdateTimestamp`) populated correctly — the initial `save()` and the later state-update `save()` coalesced into one deferred flush at transaction commit, and only the `ALWAYS`-timing `@UpdateTimestamp` generator ran. Fixed with `saveAndFlush(...)` on the initial `Delivery` creation, forcing a real INSERT (with `@CreationTimestamp` populated) before any later mutation. Not caught by the H2 test suite (which doesn't assert `createdAt`) — only found by the real-Postgres manual check.
- `ddl-auto: update` cannot evolve a column from nullable to `NOT NULL` when the (local, disposable) Postgres volume already has rows — hit this adding `DeliveryAttempt.deliveryId`. Recreating the volume (`docker compose down -v && up -d`) is the workaround for now; see Open decisions for the longer-term fix (real migrations).

## In progress

- None. See Next.

## Next

1. Maintainer reviews `.ai/constitution/` (still drafted/unreviewed — open since the foundation was adopted).
2. Begin Spec 003: Kafka-backed internal delivery and the Transactional Outbox pattern, replacing Spec 002's in-process retry loop — see ADR-0003 and `docs/architecture/system-design.md` ("Target reliability structure"). This is the point where `docker-compose.yml` needs a Kafka service added.
3. Add an automated Testcontainers-based test against real Postgres, not just H2 + manual curl verification — two real bugs (the Spec 001 `@Lob`/OID issue and the Spec 002 `createdAt` flush-timing issue) were only caught by manual Postgres verification, not by the H2-only test suite. A recurring pattern, not a one-off.
4. Introduce real schema migrations (e.g. Flyway or Liquibase) instead of `ddl-auto: update` — `update` cannot evolve nullable columns to `NOT NULL` against existing local data (hit this in Spec 002; worked around by recreating the Docker volume, which won't be acceptable once there's data worth keeping).
5. Decide whether/when to add this service to `cleanbrain-me-infra` (namespace `cleanbrain-me-relayhub-java`, Gateway listener for `relayhub-java.developer.cleanbrain.me`) — not near-term; V1 targets local Docker Compose only.

## Open decisions

- Constitution content in `.ai/constitution/` is drafted, not yet maintainer-reviewed.
- Whether a `relayhub-node` (or other language) sibling repository will actually be built, and when.
- Exact CI/CD model for this repository — not yet designed.
- Whether `application.yml`'s hardcoded local Postgres credentials should move to environment variables before this goes anywhere beyond a personal dev machine.
- `Subscription.retryPolicy` remains an unparsed free-text field; Spec 002 applies one fixed retry policy (3 attempts, 200ms/400ms backoff) to every Subscription regardless of what that field says (see ADR-0003).
- Whether to keep `ddl-auto: update` a while longer or move to migrations now (Next item 4) — no data worth preserving exists yet, so the cost of staying on `update` a bit longer is low, but the gap will only get more expensive to close later.

## Known constraints

- Kafka/Outbox do not exist yet; every delivery (including retries) runs in-process/synchronously within the ingress (or replay) request. A `PENDING` delivery is not durable across a crash mid-retry — see ADR-0003.
- No CI workflow exists yet for this repository.
- The Postgres path is verified only manually (curl against a locally running `bootRun`), not by an automated test — see Next item 3.

## Phase 1 exit criteria (met)

- `./gradlew test` passes (H2-backed).
- `docker compose up -d` + `./gradlew bootRun` verified against real Postgres 16, including a manual replay of the Spec 001 scenario end-to-end.
- The Spec 001 acceptance scenario is reproducible and verified, not just believed to compile.

## Spec 002 exit criteria (met)

- `./gradlew test` passes, including `DlqReplayIdempotencyTest`.
- The Spec 002 acceptance scenario (Target B: fail x3 -> DEAD -> replay -> SUCCEEDED; duplicate idempotency key -> deduplicated, no new Event/Deliveries) is reproducible and verified against both H2 (automated test) and real Postgres (manual curl replay, 2026-09-10).
