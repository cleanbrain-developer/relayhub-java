# Current State

Last updated: 2026-09-10

## Current phase

`phase-2b-kafka-outbox` — the Reliability phase's incremental build (ADR-0003) is now complete: Spec 002 (Retry/Backoff/DLQ/Replay/idempotency) and Spec 003 (Kafka-backed delivery via Transactional Outbox) are both implemented and verified, against H2/EmbeddedKafka (automated tests) and real Postgres/Kafka (manual curl scenarios). `docs/architecture/system-design.md`'s originally-described "Phase 2: Reliability" is now fully built, just delivered as two specs instead of one change.

## Completed

- Captured RelayHub's product/architecture design from the accepted design context (2026-09-10) and persisted it into `docs/product/`, `docs/architecture/system-design.md`, and `docs/decisions/`.
- Adopted the Agent Development Starter foundation (`PROJECT.yaml`, `CLAUDE.md`, `.ai/constitution/`, `docs/architecture/agent-context-model.md`, `docs/architecture/repository-structure.md`, ADR-0001). Constitution reviewed and accepted by the maintainer as-is (2026-09-10), after being exercised in practice.
- Decided and recorded the Java 21/Spring Boot core stack decision (ADR-0002), including the `relayhub-<lang>` naming convention for future sibling repositories.
- **Spec 001** (`specs/001-push-event-delivery/`): Gradle/Spring Boot 3.3.4 (Java 21 toolchain) scaffold with the `source`, `sourceevent`, `target`, `subscription`, `event`, `mapping`, `delivery`, `ingress`, `common` modules; registration REST APIs; auto-generated per-Source-Event Ingress URL with REST-semantics method mapping; JSONPath extraction; optional JSON Schema validation; Canonical Event persistence; `JsonNode`-tree payload mapping; HTTP delivery to Targets.
- **Spec 002** (`specs/002-retry-dlq-replay/`, ADR-0003): a `Delivery` entity (`PENDING`/`SUCCEEDED`/`DEAD`) wrapping the `DeliveryAttempt` history; retry + backoff (3 attempts, 200ms/400ms); DLQ as the `DEAD` state; `POST /api/deliveries/{id}/replay`; `GET /api/deliveries?eventId=` (Delivery-level) and `GET /api/deliveries/{id}/attempts` (attempt-level); idempotency-key deduplication at ingress.
- **Spec 003** (`specs/003-kafka-outbox/`, ADR-0003): replaced Spec 002's in-process retry loop with the durable pipeline `Ingress Transaction -> Event + OutboxEvent (same transaction) -> OutboxPublisher (scheduled poll) -> Kafka (relayhub.delivery-tasks) -> DeliveryWorker (@KafkaListener) -> DeliveryService.deliver(...)` (Spec 002's retry/DLQ logic reused unchanged). `docker-compose.yml` gained a single-node KRaft Kafka service (`apache/kafka:3.8.0`, no extra config needed). Ingress is now asynchronous — the response confirms the Event and its Outbox rows were durably stored, not that delivery completed.
- All three specs verified for real, not just compiled: `./gradlew test` passes (`RelayHubApplicationTests`, `IngressVerticalSliceTest`, `DlqReplayIdempotencyTest` — the latter two now use `@EmbeddedKafka` + Awaitility polling for the async path) **and** manually against real Postgres + Kafka via `docker compose up -d` + `./gradlew bootRun` + curl, including confirming `outbox_events` rows reach `PUBLISHED` directly in Postgres.
- Seven implementation-driven corrections to assumptions, worth knowing for later work: (1) Spring 6's `HttpMethod` is no longer a plain enum, not JPA-`@Enumerated`-mappable — introduced `common.HttpVerb`; (2) `key` is an H2 reserved word — `hibernate.globally_quoted_identifiers: true`; (3) `@Lob String` on Postgres is OID-based and fails to read back outside its creating transaction — use `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` instead; (4) `@SpringBootTest` context caching shares one H2 database across test classes with *identical* configuration — classes with differing configuration (e.g. `@EmbeddedKafka` present/absent) get separate contexts that must not share a DB name, or one context's `create-drop` teardown drops tables out from under another context's still-running background work (hit this with `OutboxPublisher`'s `@Scheduled` poller against `RelayHubApplicationTests`; fixed by giving that test its own H2 database name); (5) `Delivery.createdAt` needed `saveAndFlush(...)`, not `save(...)`, to avoid an insert+update coalescing into one flush that only ran the `@UpdateTimestamp` generator; (6) `ddl-auto: update` cannot add a `NOT NULL` column against a Postgres volume that already has rows — recreate the volume locally; (7) a Kafka `@KafkaListener` method must itself be `@Transactional` (not just the service method it calls) when it loads entities and then accesses their lazy associations across that boundary — `DeliveryWorker.onDeliveryTask` hit `LazyInitializationException` on `subscription.getTarget()` until this was added, because `findById(...)` and `DeliveryService.deliver(...)` were otherwise two separate transactions.

## In progress

- None. See Next.

## Next

1. Add an automated Testcontainers-based test against real Postgres (and ideally real Kafka), not just H2/EmbeddedKafka + manual curl verification — three real bugs across Specs 001-003 (the `@Lob`/OID issue, the `createdAt` flush-timing issue, the H2 cross-context teardown) were only caught by manual real-infrastructure verification. A recurring pattern, not a one-off.
2. Introduce real schema migrations (e.g. Flyway or Liquibase) instead of `ddl-auto: update` — see Open decisions.
3. Address the Spec 003 "Deliberately out of scope" gap: consumer-side duplicate processing (a delivery task redelivered by Kafka after a worker crash creates a second `Delivery` row for the same (Event, Subscription) pair) — Spec 002's idempotency dedup is at ingress, not at the delivery-task level.
4. Decide whether/when to add this service to `cleanbrain-me-infra` (namespace `cleanbrain-me-relayhub-java`, Gateway listener for `relayhub-java.developer.cleanbrain.me`) — not near-term; V1 targets local Docker Compose only. Note this now means deploying Kafka too, not just the app and Postgres, which changes the resource-footprint conversation for the 2 vCPU / 4 GB `cleanbrain.me` host.

## Open decisions

- Whether a `relayhub-node` (or other language) sibling repository will actually be built, and when.
- Exact CI/CD model for this repository — not yet designed.
- Whether `application.yml`'s hardcoded local Postgres/Kafka addresses should move to environment variables before this goes anywhere beyond a personal dev machine.
- `Subscription.retryPolicy` remains an unparsed free-text field; a fixed policy (3 attempts, 200ms/400ms backoff) applies to every Subscription regardless of what that field says (see ADR-0003).
- Whether to keep `ddl-auto: update` a while longer or move to migrations now (Next item 2) — no data worth preserving exists yet, so the cost of staying on `update` a bit longer is low, but the gap only gets more expensive to close later.
- Outbox row cleanup/archival is unaddressed (Spec 003 "Deliberately out of scope") — fine for a local MVP, revisit before anything long-lived.

## Known constraints

- No CI workflow exists yet for this repository.
- Consumer-side duplicate delivery-task processing is unhandled (Next item 3).
- Kafka topic partitioning is single-partition; the worker has not been tested running as more than one instance.
- The Postgres/Kafka path is verified only manually (curl against a locally running `bootRun`), not by an automated test — see Next item 1.

## Phase 1 exit criteria (met)

- `./gradlew test` passes (H2-backed).
- `docker compose up -d` + `./gradlew bootRun` verified against real Postgres 16.
- The Spec 001 acceptance scenario is reproducible and verified, not just believed to compile.

## Spec 002 exit criteria (met)

- `./gradlew test` passes, including `DlqReplayIdempotencyTest`.
- The Spec 002 acceptance scenario is reproducible and verified against both H2 and real Postgres.

## Spec 003 exit criteria (met)

- `./gradlew test` passes with `@EmbeddedKafka` + Awaitility covering the async path (all three test classes, zero failures).
- The full scenario (register -> async ingress -> Target A SUCCEEDED / Target B DEAD via Kafka -> replay -> idempotent duplicate ingress) verified manually against real `docker compose` Postgres + Kafka, including confirming `outbox_events` rows reach `PUBLISHED` directly in the database.
- Spec 002's endpoints behave identically from the API consumer's point of view — only the trigger path changed, as intended.
