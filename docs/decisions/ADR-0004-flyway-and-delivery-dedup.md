# ADR-0004: Flyway Migrations and Delivery-Level Idempotency

- Status: Accepted
- Date: 2026-09-10
- Deciders: Project maintainer

## Context

Two gaps were flagged in `docs/status/current-state.md` after Spec 003 landed:

1. `ddl-auto: update` cannot evolve a column from nullable to `NOT NULL` against a Postgres volume
   that already has rows (hit adding `DeliveryAttempt.deliveryId` in Spec 002); the workaround was
   recreating the local Docker volume, which stops being acceptable once real data exists.
2. Spec 003's "Deliberately out of scope" gap: a delivery task redelivered by Kafka (e.g. after
   `DeliveryWorker` crashes before its consumer offset commits) would call
   `DeliveryService.deliver(...)` again and create a second `Delivery` row for the same (Event,
   Subscription) pair, re-running the whole retry loop and re-calling the Target.

The maintainer chose Flyway over Liquibase for the migration tool — the more widely used option in
the Spring Boot ecosystem, and Spring Boot's own reference documentation lists it first; its
plain-SQL migrations also fit this project's existing plain-SQL-first tooling choices (no XML/YAML
changeset format to learn).

## Decision

### Schema migrations: Flyway

- `db/migration/V1__init_schema.sql` is the baseline, hand-cleaned from the actual
  Hibernate-`ddl-auto: update`-generated Postgres schema (dumped via `pg_dump --schema-only`) —
  meaningful constraint names replace Hibernate's auto-generated hashes, but every column type,
  nullability, and check constraint matches exactly.
- `spring.jpa.hibernate.ddl-auto` is `validate` outside the `test` profile: Hibernate now only
  confirms entities match the Flyway-owned schema; it never generates or alters DDL there.
- The `test` profile (H2) keeps `ddl-auto: create-drop` and disables Flyway (`spring.flyway.enabled:
  false`) — the migration SQL is Postgres-specific and unexercised by the fast H2 suite by design.
  `PostgresKafkaIntegrationTest` (Testcontainers, real Postgres) is what actually runs `V1__init_schema.sql`
  end to end, and does so on every `./gradlew test`.

### Delivery-level idempotency

- Added a unique constraint on `deliveries (event_id, subscription_id)`.
- `DeliveryService.deliver(...)` now checks `DeliveryRepository.findByEventIdAndSubscriptionId(...)`
  first; if a `Delivery` already exists for the pair, it returns that row without re-attempting
  delivery — this is the common case (sequential redelivery after a worker restart) and needs
  nothing beyond a SELECT.
- The true-concurrent case (two overlapping transactions racing on the same pair, only realistically
  possible during a brief consumer-group rebalance, since a single Kafka partition is otherwise
  processed by one consumer at a time) is deliberately **not** caught and recovered in-process. A
  first implementation attempt did catch `DataIntegrityViolationException` and re-queried inside the
  same transaction — but Postgres marks a transaction unusable for further statements once one
  statement violates a constraint, so that fallback query would itself fail. The constraint
  violation is instead left to propagate out of the `@KafkaListener`-invoked method; Spring Kafka's
  normal redelivery retries the message, and the retry's own SELECT then finds the row the other
  transaction committed.

## Consequences

### Positive

- Real schema changes going forward are explicit, versioned, reviewable SQL files instead of
  Hibernate inferring DDL — the exact gap that caused the Spec 002 NOT-NULL migration failure is
  closed.
- `PostgresKafkaIntegrationTest` now exercises the actual migration path automatically, not just
  the schema Hibernate happens to generate in a test run.
- Delivery-task redelivery (the Spec 003 known gap) is now handled for its common case without new
  infrastructure, and the rare race case fails safe (retried, not silently duplicated or lost).

### Costs and risks

- Every future schema change needs a new `V<n>__description.sql` file and manual review that it's
  compatible with `validate` — this is a deliberate cost (explicitness) traded for the removed
  `ddl-auto: update` risk.
- The H2 test profile no longer proves the migrations themselves apply cleanly; only
  `PostgresKafkaIntegrationTest` does, and it requires Docker.
- The concurrent-race path for delivery-task dedup is not covered by an automated test (it would
  require deliberately racing two transactions, which is disproportionate effort for a
  rebalance-window-only edge case) — accepted as a known, documented gap rather than solved with
  more complex code.

## Alternatives considered

### Liquibase instead of Flyway

Liquibase's changeset model (XML/YAML/JSON, with built-in diffing and rollback authoring) is more
feature-rich, but Flyway's plain-SQL-first approach was judged simpler for a project already
comfortable authoring raw SQL, and is the more common default in current Spring Boot projects.

### Catch-and-recover `DataIntegrityViolationException` for delivery dedup

Rejected after discovering the Postgres aborted-transaction behavior made the naive
catch-and-re-query pattern unsafe within one transaction (see "Decision" above). A `REQUIRES_NEW`
nested-transaction variant was considered but adds a self-invocation/proxy complication (would need
a second collaborator bean) for a race window narrow enough that "fail and let Kafka retry" is a
reasonable, much simpler answer.
