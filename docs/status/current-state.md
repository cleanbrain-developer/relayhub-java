# Current State

Last updated: 2026-09-10

## Current phase

`phase-1-vertical-slice` — the foundation is committed and the first thin vertical slice (Spec 001) is implemented and passing its acceptance test. `PROJECT.yaml` reflects this (`delivery.implementation_present: true`, `lifecycle: active-development`, `current_phase.id: phase-1-vertical-slice`).

## Completed

- Captured RelayHub's product/architecture design from the accepted design context (2026-09-10) and persisted it into `docs/product/`, `docs/architecture/system-design.md`, and `docs/decisions/`.
- Adopted the Agent Development Starter foundation (`PROJECT.yaml`, `CLAUDE.md`, `.ai/constitution/`, `docs/architecture/agent-context-model.md`, `docs/architecture/repository-structure.md`, ADR-0001).
- Decided and recorded the Java 21/Spring Boot core stack decision (ADR-0002), including the `relayhub-<lang>` naming convention for future sibling repositories.
- Created and implemented `specs/001-push-event-delivery/`: `git init`, GitHub repository `relayhub-java` (public) created and pushed, foundation committed as baseline, bootstrap acceptance test run and passed via an isolated subagent session (no gaps beyond the already-known constitution-review item).
- Implemented Phase 1 end-to-end: Gradle/Spring Boot 3.3.4 (Java 21 toolchain) project scaffold with the `source`, `sourceevent`, `target`, `subscription`, `event`, `mapping`, `delivery`, `ingress`, `common` modules; registration REST APIs; auto-generated per-Source-Event Ingress URL with REST-semantics method mapping (CREATED=POST/REPLACED=PUT/PATCHED=PATCH/DELETED=DELETE); JSONPath extraction (`resourceIdPath` required, `occurredAtPath`/idempotency optional) via Jayway JsonPath with the Jackson `JsonNode` provider; optional JSON Schema validation (networknt); Canonical Event persistence; Jackson `JsonNode`-tree-based target payload mapping (never string `replace()`); synchronous-for-now HTTP delivery to Targets with Delivery Attempt history.
- Verified for real, not just compiled: `./gradlew test` passes two tests — a context-load test and `IngressVerticalSliceTest`, which runs the actual Spec 001 acceptance scenario (register Source/Source Event/Target/Subscription, POST to the generated Ingress URL, assert the mapped payload — including a preserved non-string `active: true` boolean — was received by a WireMock-stubbed Target) against a real Spring context with H2. Ran locally with a JDK 21 toolchain (`~/.jdks/openjdk-21.0.1`) not currently on `PATH`; the Gradle wrapper (`gradlew`/`gradlew.bat`) was bootstrapped from Gradle 8.10 so `PATH`/`JAVA_HOME` don't need to be preconfigured for future runs.
- `docker compose up -d` verified for real (2026-09-10, Docker daemon turned on by the maintainer): Postgres 16 starts cleanly, `./gradlew bootRun` connects to it, and the full Spec 001 scenario was replayed manually against it via curl (register Source/Source Event/Target/Subscription, POST to the generated Ingress URL, confirm the mapped payload — including the preserved `active: true` boolean — arrived at a local Node echo server, and confirm `/api/events/{id}` and `/api/deliveries?eventId=` both return the persisted history). `docker-compose.yml` currently defines Postgres only — Kafka/Redis are deferred to the Reliability phase, per scope.
- Three implementation-driven corrections to `docs/architecture/system-design.md`'s assumptions, worth knowing for later specs: (1) Spring Framework 6's `HttpMethod` is no longer a plain enum, so it cannot be JPA-`@Enumerated`-mapped directly — introduced `common.HttpVerb` as the persisted stand-in, converted to/from `HttpMethod` only at the HTTP boundary; (2) `key` is a reserved word in H2's grammar (used as a test database), so `spring.jpa.properties.hibernate.globally_quoted_identifiers: true` is set — harmless on Postgres, required for the H2 test profile; (3) `@Lob String` on Postgres maps to an OID-based large object, which fails to read back outside the transaction that created it (`Unable to access lob stream`, caught live against real Postgres, not just H2) — every large-text field (`Event.payload`, `SourceEvent.payloadSchema`, `Subscription.targetPayloadTemplate`, `DeliveryAttempt.responseBody`/`errorMessage`) uses `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` instead, which is portable across H2 and Postgres without OID semantics.

## In progress

- None. See Next.

## Next

1. Maintainer reviews `.ai/constitution/` (still drafted/unreviewed — this has been open since the foundation was adopted and Phase 1 implementation did not resolve it).
2. Begin the Reliability phase (Spec 002+): Kafka-backed internal delivery, Transactional Outbox, Retry/Backoff, DLQ, Replay, Idempotency enforcement — see `docs/product/goals.md` and `docs/architecture/system-design.md`.
3. Add a second Target/Subscription to the acceptance test (intentional failure -> retry -> DLQ -> replay), matching the full 13-step scenario in the original design context — deferred because Retry/DLQ/Replay don't exist yet (Next item 2).
4. Add an automated Testcontainers-based test against real Postgres, not just H2 + manual curl verification — the OID/`@Lob` bug above was only caught manually and would not have been caught by the existing H2-only test suite.
5. Decide whether/when to add this service to `cleanbrain-me-infra` (namespace `cleanbrain-me-relayhub-java`, Gateway listener for `relayhub-java.developer.cleanbrain.me`) — not near-term; V1 targets local Docker Compose only.

## Open decisions

- Constitution content in `.ai/constitution/` is drafted, not yet maintainer-reviewed.
- Whether a `relayhub-node` (or other language) sibling repository will actually be built, and when.
- Exact CI/CD model for this repository — not yet designed.
- Whether `application.yml`'s hardcoded local Postgres credentials should move to environment variables before this goes anywhere beyond a personal dev machine (currently fine for local-only Phase 1, called out here so it isn't forgotten).

## Known constraints

- Retry/Backoff/DLQ/Replay/Idempotency-enforcement/Kafka/Outbox do not exist yet; every delivery is a single in-process synchronous HTTP attempt.
- No CI workflow exists yet for this repository.
- The Postgres path is verified only manually (curl against a locally running `bootRun`); there is no automated test exercising real Postgres yet (see Next item 4) — the H2 test suite alone missed the OID/`@Lob` bug this session found and fixed.

## Phase 1 exit criteria (met)

- `./gradlew test` passes (H2-backed).
- `docker compose up -d` + `./gradlew bootRun` verified against real Postgres 16, including a manual replay of the Spec 001 scenario end-to-end (registration through delivery history).
- The Spec 001 acceptance scenario (single-Target slice) is reproducible and verified, not just believed to compile.
- `docs/status/current-state.md` reflects real, verified progress.
