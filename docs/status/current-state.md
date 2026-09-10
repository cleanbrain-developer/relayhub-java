# Current State

Last updated: 2026-09-10

## Current phase

`phase-2c-migrations-and-dedup` — the Reliability phase (ADR-0003) is complete, and both follow-up gaps it left open (ADR-0004) are closed: real schema migrations (Flyway) replaced `ddl-auto: update`, and delivery-task-level idempotency now protects against Kafka redelivery creating duplicate `Delivery` rows. All work verified against H2/EmbeddedKafka (automated) and real Postgres/Kafka (automated via Testcontainers, plus manual `docker compose` scenarios for Specs 001-003). CI (`.github/workflows/ci.yml`) now runs the full suite on every push/PR.

## Completed

- Captured RelayHub's product/architecture design from the accepted design context (2026-09-10) and persisted it into `docs/product/`, `docs/architecture/system-design.md`, and `docs/decisions/`.
- Adopted the Agent Development Starter foundation. Constitution reviewed and accepted by the maintainer as-is (2026-09-10), after being exercised in practice.
- Decided and recorded the Java 21/Spring Boot core stack decision (ADR-0002), including the `relayhub-<lang>` naming convention for future sibling repositories.
- **Spec 001** (`specs/001-push-event-delivery/`): registration REST APIs, auto-generated Ingress URLs, JSONPath extraction, JSON Schema validation, Canonical Event persistence, `JsonNode`-tree payload mapping, HTTP delivery to Targets.
- **Spec 002** (`specs/002-retry-dlq-replay/`, ADR-0003): `Delivery` entity (`PENDING`/`SUCCEEDED`/`DEAD`), retry + backoff, DLQ, `POST /api/deliveries/{id}/replay`, idempotency-key deduplication at ingress.
- **Spec 003** (`specs/003-kafka-outbox/`, ADR-0003): Kafka-backed delivery via Transactional Outbox — `Ingress Transaction -> Event + OutboxEvent (same transaction) -> OutboxPublisher -> Kafka -> DeliveryWorker -> DeliveryService.deliver(...)`. Ingress is now asynchronous. `docker-compose.yml` gained a single-node KRaft Kafka service (`apache/kafka:3.8.0`).
- **`PostgresKafkaIntegrationTest`** (Testcontainers, real Postgres 16 + `confluentinc/cp-kafka:7.7.1`): runs the combined Spec 001/002/003 scenario through `./gradlew test` itself against real infrastructure and the actual (non-H2-test-profile) `application.yml` settings.
- **ADR-0004** (2026-09-10): closed both gaps Spec 003 left open.
  - **Flyway**, chosen over Liquibase as the more common default in current Spring Boot projects and for its plain-SQL fit with this codebase. `db/migration/V1__init_schema.sql` is the baseline, hand-cleaned from the actual Hibernate-generated Postgres schema (`pg_dump --schema-only`) — same columns/types/nullability/checks, meaningful constraint names instead of Hibernate's hashes. `ddl-auto` is now `validate` outside the `test` profile; the `test` profile (H2) keeps `create-drop` and disables Flyway, since the migration SQL is Postgres-specific — `PostgresKafkaIntegrationTest` is what actually exercises `V1__init_schema.sql`, on every `./gradlew test` run.
  - **Delivery-level idempotency**: a unique constraint on `deliveries (event_id, subscription_id)` plus a SELECT-before-INSERT check in `DeliveryService.deliver(...)` means a Kafka-redelivered delivery task (e.g. after a worker crash before its offset commits) returns the existing `Delivery` instead of creating a duplicate and re-running the retry loop. The rare true-concurrent case (only realistically possible during a consumer-group rebalance) is deliberately left to propagate as an uncaught constraint violation, letting Kafka's normal redelivery retry — an initial catch-and-recover-in-the-same-transaction attempt was rejected after discovering Postgres marks a transaction unusable for further statements once one is violated, breaking the fallback query. Verified directly by `DeliveryDedupTest` (calls `deliver()` twice for the same pair; asserts one `Delivery`, one attempt).
- **CI** (`.github/workflows/ci.yml`): runs `./gradlew test` on every push/PR to `master` using GitHub's `ubuntu-latest` runners (Docker available by default, so `PostgresKafkaIntegrationTest` runs there too), uploads the test report as a build artifact. Test-only — no build/publish/deploy job, since there is no deployment target yet.
- Ten implementation-driven corrections to assumptions, worth knowing for later work: (1) Spring 6's `HttpMethod` is no longer a plain enum, not JPA-`@Enumerated`-mappable — `common.HttpVerb`; (2) `key` is an H2 reserved word — `hibernate.globally_quoted_identifiers: true`; (3) `@Lob String` on Postgres is OID-based, fails to read back outside its creating transaction — `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` instead; (4) `@SpringBootTest` context caching shares one H2 database across test classes with *identical* configuration only — differently-configured classes get separate contexts that must not share a DB name, or one context's `create-drop` teardown drops tables out from under another's still-running background work; (5) `Delivery.createdAt` needed `saveAndFlush(...)`, not `save(...)` (insert+update were coalescing into one flush that only ran the `@UpdateTimestamp` generator); (6) `ddl-auto: update` cannot add a `NOT NULL` column against a Postgres volume with existing rows — this is exactly what ADR-0004's Flyway migration now avoids; (7) a `@KafkaListener` method must itself be `@Transactional` when it loads entities and later code accesses their lazy associations across what would otherwise be a second transaction; (8) Testcontainers' `KafkaContainer` only works with `confluentinc/cp-kafka` images, not `apache/kafka` (used by `docker-compose.yml`) — different entrypoint/scripts; (9) a raw JPQL `@Query` must use the real association path (`s.sourceEvent.id`), not the derived-query-style name (`s.sourceEventId`) that only works in Spring Data's *method-name* parsing; (10) catching `DataIntegrityViolationException` and querying again inside the *same* transaction doesn't work on Postgres — a violated statement poisons the transaction for everything after it, including the recovery query itself.

## In progress

- None. See Next.

## Next

1. Decide whether/when to add this service to `cleanbrain-me-infra` (namespace `cleanbrain-me-relayhub-java`, Gateway listener for `relayhub-java.developer.cleanbrain.me`) — not near-term (confirmed again 2026-09-10); V1 targets local Docker Compose only. Now means deploying Kafka too, not just the app and Postgres.
2. Add `V2__...` migrations as real schema changes arise — `ddl-auto` will no longer silently apply them; each needs a deliberate SQL file and a `validate` check.
3. CI currently only runs tests on push/PR (`.github/workflows/ci.yml`) — no build/publish/deploy job exists, deliberately, since there is no deployment target yet (item 1). Add one once item 1 is decided.

## Open decisions

- Whether a `relayhub-node` (or other language) sibling repository will actually be built, and when.
- Whether `application.yml`'s hardcoded local Postgres/Kafka addresses should move to environment variables before this goes anywhere beyond a personal dev machine.
- `Subscription.retryPolicy` remains an unparsed free-text field; a fixed policy (3 attempts, 200ms/400ms backoff) applies to every Subscription regardless of what that field says (see ADR-0003).
- Outbox row cleanup/archival is unaddressed (Spec 003 "Deliberately out of scope") — fine for a local MVP, revisit before anything long-lived.

## Known constraints

- Kafka topic partitioning is single-partition; the worker has not been tested running as more than one instance.
- Delivery-task dedup's true-concurrent race path (consumer-group-rebalance window only) is not covered by an automated test — accepted as a documented gap rather than added complexity (see ADR-0004 "Costs and risks").

## Phase 1 exit criteria (met)

- `./gradlew test` passes (H2-backed).
- `docker compose up -d` + `./gradlew bootRun` verified against real Postgres 16.
- The Spec 001 acceptance scenario is reproducible and verified, not just believed to compile.

## Spec 002 exit criteria (met)

- `./gradlew test` passes, including `DlqReplayIdempotencyTest`.
- The Spec 002 acceptance scenario is reproducible and verified against both H2 and real Postgres.

## Spec 003 exit criteria (met)

- `./gradlew test` passes with `@EmbeddedKafka` + Awaitility covering the async path **and** `PostgresKafkaIntegrationTest` (Testcontainers, real Postgres + Kafka) covering the same scenario end to end.
- The full scenario (register -> async ingress -> Target A SUCCEEDED / Target B DEAD via Kafka -> replay -> idempotent duplicate ingress) is verified both automatically (Testcontainers) and manually against `docker compose` Postgres + Kafka.
- Spec 002's endpoints behave identically from the API consumer's point of view — only the trigger path changed, as intended.

## ADR-0004 exit criteria (met)

- `./gradlew test` passes with all five test classes (`RelayHubApplicationTests`, `IngressVerticalSliceTest`, `DlqReplayIdempotencyTest`, `PostgresKafkaIntegrationTest`, `DeliveryDedupTest`), zero failures.
- `PostgresKafkaIntegrationTest` applies `V1__init_schema.sql` against a fresh Postgres container and `ddl-auto: validate` passes — the migration SQL matches the entities exactly.
- `DeliveryDedupTest` proves a second `deliver()` call for an already-delivered (Event, Subscription) pair returns the existing `Delivery` and creates no second `DeliveryAttempt`.
