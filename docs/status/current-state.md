# Current State

Last updated: 2026-09-10

## Current phase

`phase-3-observability` — the original design's full roadmap is now built: Specs 001-003 (vertical slice, reliability, Kafka/Outbox), ADR-0004 (Flyway migrations, delivery-task dedup), CI, and Spec 004 (`specs/004-observability/`) — metrics, dashboards, tracing, and reproducible demo/load scripts. Every phase verified against real infrastructure (Postgres, Kafka, Prometheus, Grafana, Zipkin via `docker compose`), not just believed to work. Now extending Spec 004 with demo-profile seed data (done) and a continuous-traffic/random-failure simulator (`relayhub-demo-systems`, in progress) to give the future developer-site observability dashboard real, unattended activity to show.

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
- **CI** (`.github/workflows/ci.yml`): runs `./gradlew test` on every push/PR to `master` using GitHub's `ubuntu-latest` runners (Docker available by default, so `PostgresKafkaIntegrationTest` runs there too), uploads the test report as a build artifact. Test-only — no build/publish/deploy job, since there is no deployment target yet. Confirmed actually green on GitHub (not just written), via `gh run watch` on the real push (run [34444172684](https://github.com/cleanbrain-developer/relayhub-java/actions/runs/34444172684), 2m47s, all steps passed).
- **Spec 004** (`specs/004-observability/`, 2026-09-10): the design roadmap's final phase.
  - **Metrics**: Actuator + Micrometer Prometheus registry (`/actuator/prometheus`), custom counters/timers in `IngressService` (ingress events by outcome), `OutboxPublisher` (publish success/failure), `DeliveryService` (attempts by status, terminal state, replay outcome, attempt-duration timer).
  - **Dashboards**: `docker-compose.yml` gained Prometheus (scraping the app via `host.docker.internal`, since the app runs on the host, not in Compose) and Grafana, provisioned with the Prometheus datasource and a starter "RelayHub" dashboard (`observability/`).
  - **Tracing**: Micrometer Tracing -> OpenTelemetry -> Zipkin (`docker-compose.yml` Zipkin service); `management.tracing.sampling.probability: 1.0` for local dev.
  - **Demo/load scripts**: `scripts/dlq-replay-demo.sh` (scripted failure -> DLQ -> replay) and `scripts/load-check.sh` (N concurrent ingress requests, confirms all reach `SUCCEEDED`) — both use a small local Node echo target (no new project dependency), consistent with this project's manual-verification pattern throughout.
  - All of it verified live, not just written: `relayhub_*` metrics observed populating; Prometheus `up{job="relayhub"}=1`; Grafana's own API confirmed the datasource and dashboard both auto-provisioned; Zipkin returned a real `http post /ingress/v1/**` trace span; both scripts ran to a `PASS` exit code against a live instance.
  - Two real portability bugs found while writing the scripts: (a) Node (a native Windows binary under Git Bash) can't resolve bash's POSIX-style temp paths — fixed with `cygpath -w`; (b) a plain `&`/`wait` background-job loop hung indefinitely under Git Bash on Windows past a handful of concurrent subshells — replaced with `xargs -P`.
- Ten implementation-driven corrections to assumptions from Specs 001-003/ADR-0004, worth knowing for later work: (1) Spring 6's `HttpMethod` is no longer a plain enum, not JPA-`@Enumerated`-mappable — `common.HttpVerb`; (2) `key` is an H2 reserved word — `hibernate.globally_quoted_identifiers: true`; (3) `@Lob String` on Postgres is OID-based, fails to read back outside its creating transaction — `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` instead; (4) `@SpringBootTest` context caching shares one H2 database across test classes with *identical* configuration only — differently-configured classes get separate contexts that must not share a DB name, or one context's `create-drop` teardown drops tables out from under another's still-running background work; (5) `Delivery.createdAt` needed `saveAndFlush(...)`, not `save(...)` (insert+update were coalescing into one flush that only ran the `@UpdateTimestamp` generator); (6) `ddl-auto: update` cannot add a `NOT NULL` column against a Postgres volume with existing rows — this is exactly what ADR-0004's Flyway migration now avoids; (7) a `@KafkaListener` method must itself be `@Transactional` when it loads entities and later code accesses their lazy associations across what would otherwise be a second transaction; (8) Testcontainers' `KafkaContainer` only works with `confluentinc/cp-kafka` images, not `apache/kafka` (used by `docker-compose.yml`) — different entrypoint/scripts; (9) a raw JPQL `@Query` must use the real association path (`s.sourceEvent.id`), not the derived-query-style name (`s.sourceEventId`) that only works in Spring Data's *method-name* parsing; (10) catching `DataIntegrityViolationException` and querying again inside the *same* transaction doesn't work on Postgres — a violated statement poisons the transaction for everything after it, including the recovery query itself.

- **Demo-profile seed data** (`src/main/java/me/cleanbrain/relayhub/demo/DemoDataSeeder.java`, `application-demo.yml`, 2026-09-10): a `CommandLineRunner` gated on the `demo` Spring profile idempotently registers a concrete cross-system scenario on every startup. Off by default; reuses `SourceService`/`SourceEventService`/`TargetService`/`SubscriptionService` (same validation path as the HTTP API), not a direct-repository shortcut.
  - **Scenario** (settled after extensive back-and-forth on what would be both a *legitimate* RelayHub use case — genuinely cross-system, not an intra-MSA call RelayHub wouldn't need to broker — and visually interesting on a future dashboard): a simulated airline flight-status system, Source `demo-flightstatus`, emits `flight-created` (Operation.CREATED, so `POST /ingress/v1/demo-flightstatus/flight-created`) and `flight-status-updated` (Operation.PATCHED, so `PATCH .../flight-status-updated`) events. Two external Targets subscribe, each via its own mapping template: `demo-airport-display` (gets every field: flightNo/status/gate/delayMinutes; always succeeds) and `demo-travelapp-vendor` (gets only flightNo+status — a real third party wouldn't get everything; will randomly fail/timeout once `relayhub-demo-systems` exists, feeding the DLQ demo).
  - Verified live against real Postgres/Kafka, including the field-mapping itself: a temporary local echo listener on the simulator port confirmed each Target's template renders the right field subset with real interpolated values (not literal `${...}` placeholders), the CREATE vs PATCH HTTP-verb routing works, and an unreachable Target correctly exhausts its 3 retries into `DEAD` (the DLQ path). Idempotency re-verified (restart re-runs the seeder with zero "Seeded ..." lines, no duplicate Subscriptions).

- **`relayhub-demo-systems`** (2026-09-11, [github.com/cleanbrain-developer/relayhub-demo-systems](https://github.com/cleanbrain-developer/relayhub-demo-systems)): a new sibling repo, Node.js/TypeScript/Express, that plays both the flight-status Source and its two Targets. A `setInterval` scheduler ticks every 5s with no manual trigger, creating/advancing simulated flights and POSTing/PATCHing them into this repo's Ingress API. `/targets/airport-display` always succeeds; `/targets/travelapp-vendor` randomly returns 503 immediately or stalls ~6s then 504 (its own internal logic, not RelayHub's or a script's). Verified live end to end against a real running `demo`-profile instance: continuous ticks, both Targets receiving correctly-mapped requests, and — unattended — a real Delivery reaching `DEAD` (`relayhub_delivery_terminal_total{state="dead"}` incremented) purely from the simulator's own flakiness.
- **Docker Compose integration** (2026-09-11): `relayhub-demo-systems` gained a multi-stage `Dockerfile`, and `docker-compose.yml` gained an opt-in `demo`-profile `demo-systems` service (`docker compose --profile demo up -d demo-systems`) with a build context assuming the two repos are checked out as siblings (`../../node/relayhub-demo-systems`). The app itself still runs on the host, so the container reaches it via `host.docker.internal`. Verified live: the containerized simulator built, started, reached the host app, and drove a real Delivery to `DEAD` from inside Docker — same behavior as running it outside Docker.

## In progress

- None right now for the demo-observability track — `relayhub-demo-systems` is built, containerized, and wired into `docker-compose.yml`.

## Next

The original design roadmap (Phases 1-3) is now fully built. What's left is maintenance-shaped, not new-phase-shaped, plus the demo-observability work above:

1. Decide whether/when to add this service to `cleanbrain-me-infra` (namespace `cleanbrain-me-relayhub-java`, Gateway listener for `relayhub-java.developer.cleanbrain.me`) — not near-term (confirmed again 2026-09-10); V1 targets local Docker Compose only. Now means deploying Kafka, Prometheus, Grafana, and Zipkin too, not just the app and Postgres — worth reassessing which of the observability stack (if any) actually belongs in production versus staying local-dev-only when this decision is revisited. Eventually `relayhub-demo-systems` would deploy alongside it.
2. Add `V2__...` migrations as real schema changes arise — `ddl-auto` will no longer silently apply them; each needs a deliberate SQL file and a `validate` check.
3. CI currently only runs tests on push/PR (`.github/workflows/ci.yml`) — no build/publish/deploy job exists, deliberately, since there is no deployment target yet (item 1). Add one once item 1 is decided.
4. `management.tracing.sampling.probability: 1.0` traces every request — fine at personal-project scale, would need dialing down before any real traffic volume (see `specs/004-observability/spec.md`).

## Open decisions

- Whether a `relayhub-node` (or other language) sibling repository will actually be built, and when.
- Whether `application.yml`'s hardcoded local Postgres/Kafka addresses should move to environment variables before this goes anywhere beyond a personal dev machine.
- `Subscription.retryPolicy` remains an unparsed free-text field; a fixed policy (3 attempts, 200ms/400ms backoff) applies to every Subscription regardless of what that field says (see ADR-0003).
- Outbox row cleanup/archival is unaddressed (Spec 003 "Deliberately out of scope") — fine for a local MVP, revisit before anything long-lived.

## Known constraints

- Kafka topic partitioning is single-partition; the worker has not been tested running as more than one instance.
- Delivery-task dedup's true-concurrent race path (consumer-group-rebalance window only) is not covered by an automated test — accepted as a documented gap rather than added complexity (see ADR-0004 "Costs and risks").
- Prometheus's scrape target (`host.docker.internal:8080`, in `observability/prometheus/prometheus.yml`) assumes Docker Desktop; native Linux Docker would need a different approach (e.g. `--add-host` is already set via `extra_hosts` in `docker-compose.yml`, but this hasn't been verified outside Docker Desktop).
- No alerting/SLOs/log aggregation — deliberately out of scope for a personal project (see `specs/004-observability/spec.md`).

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

## Spec 004 exit criteria (met)

- `docker compose up -d` brings up Postgres, Kafka, Prometheus, Grafana, and Zipkin; the app connects to all of them (verified live, 2026-09-10).
- `/actuator/prometheus` exposes the custom counters/timer and they visibly increment across a real ingress -> delivery flow.
- Prometheus actually scrapes the app (`up{job="relayhub"}` = 1); Grafana's own API confirms the provisioned Prometheus datasource and "RelayHub" dashboard both auto-loaded.
- A real ingress request's trace is visible in Zipkin (`http post /ingress/v1/**` span observed via `/api/v2/traces`).
- `scripts/dlq-replay-demo.sh` and `scripts/load-check.sh` both run unattended against a live instance and exit 0 (`PASS`).
- CI confirmed green on GitHub for this change too (run [34472249144](https://github.com/cleanbrain-developer/relayhub-java/actions/runs/34472249144), 2m54s, all steps passed) — the new Micrometer/tracing/Actuator dependencies and config didn't break the fast test suite.
