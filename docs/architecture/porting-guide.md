# Porting Guide: Building a `relayhub-<lang>` Sibling

`docs/decisions/ADR-0002-java-spring-boot-stack.md` already decided that a same-product
implementation in a different language is a separate, sibling repository (e.g. `relayhub-node`,
`relayhub-go`), not a rewrite of this one — but it never said, concretely, what a sibling
implementation must reproduce versus what it should do idiomatically differently. This document is
that answer (self-review finding, 2026-10-02).

## What must be replicated (the product design — language-agnostic)

These are the things a port is *wrong* if it doesn't match, regardless of language:

1. **The domain model and its relationships.** `docs/architecture/domain-model.md` — Source ->
   SourceEvent -> SourceField, Target -> TargetEndpoint -> TargetField, SourceEvent <-Subscription->
   TargetEndpoint, Event -> Delivery -> DeliveryAttempt. A sibling's own database schema doesn't
   need to look like this repo's actual SQL (use whatever's idiomatic for that language/ORM), but
   the *entities and relationships* it represents do.

2. **The API contract.** `docs/architecture/api-contract.md` points at the live, generated OpenAPI
   spec (`GET /v3/api-docs` against a running instance of *this* repository, Java) — fetch it from
   there, don't hand-transcribe from the Java source. Same paths, same request/response field names
   and types, same status codes. This is the most mechanically verifiable thing to port correctly,
   and the whole reason that spec is generated rather than hand-maintained is so it stays trustworthy
   to check against.

3. **Delivery semantics.** At-least-once delivery, never "exactly once" (see the constitution's
   "external contract flexibility" and "observable failure over hidden failure" principles) —
   `docs/architecture/domain-model.md`'s "Delivery / DLQ / Replay state machine" diagram is the
   state machine to reproduce: `PENDING -> PROCESSING -> SUCCEEDED`,
   `PROCESSING -> RETRYING -> PROCESSING (loop) -> DEAD`, `DEAD -> REPLAYING -> PROCESSING`. The
   *shape* of this state machine matters more than this repo's specific backoff formula (exponential
   with optional jitter) — though matching that too is reasonable default behavior to carry over
   unless the new language's ecosystem has a clearly better-idiomatic equivalent.

4. **Idempotency guarantees.** A DB-level uniqueness constraint backing the idempotency check (not
   just an application-level check — see `docs/decisions/ADR-0004-flyway-and-delivery-dedup.md` and
   the race `IdempotencyConcurrencyTest` proves is real), scoped to
   `(source_event_id, idempotency_key)` conceptually.

5. **Ingress URL convention.** `docs/architecture/system-design.md`'s "Ingress URL convention" —
   method maps to data-change semantics (`CREATED=POST`, `REPLACED=PUT`, `PATCHED=PATCH`,
   `DELETED=DELETE`), paths are generated from registration data, never hardcoded per integration.

6. **Mapping strategy.** Type-preserving tree manipulation (this repo: Jackson `JsonNode` +
   JSONPath), never naive string `replace()` — see `system-design.md`'s "Mapping strategy" table.
   The exact template syntax (`${$.jsonPath}`) is this repo's specific choice, not necessarily
   sacred, but the *approach* (parse the template as a real tree, substitute typed values, don't
   string-munge JSON) is a hard constraint, not a style preference — a string-replace
   implementation would silently corrupt numbers/booleans/nested objects.

7. **The constitution's principles**, in full — `.specify/memory/constitution.md`: no forced
   RelayHub payload envelope on Source/Target, reliability over feature count, observable failure
   over hidden failure (retry/DLQ/replay/audit, never silently dropped), replayable structure over
   manual correction, modular monolith before distribution (no premature microservice split), real
   integration over mock-only code in tests.

8. **The hard "don't do this" list** from the original design brief (preserved here since it's
   easy to lose track of once the domain model has evolved several times): don't force a RelayHub
   envelope; don't require a Source/Target to change its own contract to integrate; don't use naive
   string-template replacement; don't claim "exactly once" delivery; don't drop a database and
   reseed instead of migrating data forward; don't split into microservices before distribution is
   proven necessary; don't show an unimplemented feature as if it were functional in the UI (see
   this repo's own `HMAC`/`OAUTH2`/`BEARER_TOKEN`/`BASIC` auth types — declared, deliberately not
   offered as selectable in the console because they don't do anything yet); don't expose secrets in
   plaintext via any API response.

## What's Java/Spring-specific (port idiomatically instead, don't transliterate)

- **Spring Security's `SecurityConfig`** — HTTP Basic + stateless + a single in-memory admin
  account is this repo's specific (intentionally minimal, see its own Javadoc) choice. A sibling
  should use whatever auth middleware is idiomatic for its framework, as long as the *policy* it
  enforces matches: `/api/**` writes need admin auth, everything else is public (see
  `security/SecurityConfig.java`'s own comment on its actual default posture — "public unless
  specifically gated" — before copying that default elsewhere).
- **JPA/Hibernate entity mapping, Flyway migrations.** Use whatever ORM/migration tool is idiomatic
  (e.g. Prisma or a query builder + node-pg-migrate for Node; `sqlc`/GORM + `golang-migrate` for
  Go). The *additive-only, no-drop-and-reseed* migration discipline (ADR-0004) is the part that must
  carry over, not Flyway specifically.
- **Spring Kafka.** Use whatever Kafka client is idiomatic. The Transactional Outbox pattern itself
  (`outbox` package — write the Event and an Outbox row in the same DB transaction, a separate
  publisher moves Outbox rows to Kafka) is the part to keep; `spring-kafka`'s specific API isn't.
- **Testcontainers (Java).** Most mainstream languages have an equivalent (Testcontainers itself has
  Node/Go/Python bindings) — the thing to keep is the *discipline*: real Postgres/Kafka in CI, not
  just an in-memory substitute, because this repo has hit real bugs (see
  `docs/status/current-state.md`'s Stage 1 production-migration incident) that only Postgres-specific
  behavior ever surfaced, never an H2-equivalent.
- **springdoc-openapi.** Use whatever generates an OpenAPI spec from the sibling's own real
  controllers/types at runtime — the point (see `api-contract.md`) is that the contract is
  generated and always in sync, never hand-maintained, not that it's specifically springdoc.
- **Package-by-feature Java layout** (`source/`, `sourceevent/`, `target/`, ... — see
  `system-design.md`'s "Recommended domain modules," corrected 2026-10-02 against the real layout).
  The *principle* (organize by domain concept, not by technical layer) is worth keeping; the literal
  directory-per-package Java convention doesn't need to map 1:1 onto Go's package conventions or a
  Node project's folder structure.
- **Gradle Kotlin DSL, the multi-stage `Dockerfile`, `ci.yml`'s exact job shape.** Use whatever
  build tool and CI steps are idiomatic for the new language — but keep the *shape* this repo's own
  2026-10-02 self-review had to retrofit in: both the frontend (if any) and the backend verified in
  CI, not just one of them (see that review's "frontend had zero automated verification" finding),
  and the Docker build must include a real type-check if the language has one, not just a bare
  transpile/bundle step.

## Practical bootstrap checklist

1. Read `docs/product/` (problem, goals, scope) — the product design, not this repo's
   implementation of it.
2. Read `docs/architecture/domain-model.md` and `docs/architecture/system-design.md` for the
   current entity model and data flow (note the correction notes in `system-design.md` where the
   original MVP-era content has drifted — those notes say what's still accurate and what isn't).
3. Fetch `GET /v3/api-docs` from a running instance of this repository (or the live production
   one, `https://relayhub-java.developer.cleanbrain.me/v3/api-docs`) for the actual, current API
   contract to implement against.
4. Read `.specify/memory/constitution.md` for the durable principles above.
5. Follow `ADR-0002`'s naming convention: repository `relayhub-<lang>`, hostname
   `relayhub-<lang>.developer.cleanbrain.me`, coordinate with `cleanbrain-me-infra` for the actual
   Kubernetes namespace/manifests (that repository, not this one, owns deployment — see
   `docs/architecture/overview.md`'s "Deployment boundary").
6. Treat this repository's test suite as an executable specification of expected behavior, not
   just this implementation's own regression tests — `IdempotencyConcurrencyTest`,
   `DeliveryRetrySchedulerTest`, `DlqReplayIdempotencyTest`, `ApiKeyAuthenticationTest`, and
   `MappingFieldRegistryValidationTest` in particular each encode a specific acceptance scenario
   worth reproducing against the new implementation, not just reading about.
7. Review `docs/status/current-state.md`'s "Known constraints"/"Open decisions" and
   `domain-model.md`'s "Known gaps" — decide deliberately whether the new implementation inherits
   each deferred decision (Filter not evaluated, no rate limiting, etc.) or makes its own different
   one; either is fine, but make it a decision, not an accident.
