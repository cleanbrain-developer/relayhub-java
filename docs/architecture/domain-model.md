# Domain Model (current)

This document is the current, accurate picture of RelayHub's domain model, delivery pipeline, and
Delivery/DLQ/Replay state machine — kept in sync with the actual code, unlike `system-design.md`'s
original MVP-era tables (which stay as the historical accepted design context; see its own note).
It reflects the integration-platform domain-model overhaul (maintainer request 2026-09-30, three
staged, independently-deployed changes — see `docs/status/current-state.md` for the dated log of
each stage's implementation and verification).

## Why this changed

Before this overhaul, `Target` had no per-endpoint request shape (`TargetField` was scoped directly
to `Target`), `Subscription` carried a raw `targetMethod`/`targetPath` pair instead of referencing a
real API contract, and per-Subscription retry tuning didn't exist (one fixed global backoff
schedule for everything). The goal was to make RelayHub read as "register and operate real system
integrations" rather than a flat event-delivery demo — without forcing a payload envelope on
Source/Target, without breaking any existing Subscription, and without dropping/reseeding
production data (see `docs/decisions/ADR-0004-flyway-and-delivery-dedup.md` and the Flyway
migrations under `src/main/resources/db/migration/` for how every change here was applied
additively against live production data).

## Entity relationships

```mermaid
erDiagram
    SOURCE ||--o{ SOURCE_EVENT : "registers"
    SOURCE_EVENT ||--o{ SOURCE_FIELD : "declares"
    TARGET ||--o{ TARGET_ENDPOINT : "exposes"
    TARGET_ENDPOINT ||--o{ TARGET_FIELD : "accepts"
    SOURCE_EVENT ||--o{ SUBSCRIPTION : "is routed by"
    TARGET_ENDPOINT ||--o{ SUBSCRIPTION : "is the destination of"
    SUBSCRIPTION ||--o{ DELIVERY : "produces (via Event)"
    EVENT ||--o{ DELIVERY : "is delivered as"
    DELIVERY ||--o{ DELIVERY_ATTEMPT : "records"

    SOURCE {
        uuid id
        string key
        string name
        AuthenticationType authenticationType
        string authenticationConfig "never echoed back"
        Status status
    }
    SOURCE_EVENT {
        uuid id
        uuid source_id
        string key
        string resourceType
        Operation operation
        HttpVerb ingressMethod
        string ingressPath "unique, auto-generated"
        string resourceIdPath "JSONPath"
        string idempotencyKeyPath "JSONPath, optional"
    }
    SOURCE_FIELD {
        uuid id
        uuid source_event_id
        string fieldKey
        string jsonPath
        FieldDataType dataType
    }
    TARGET {
        uuid id
        string key
        string baseUrl
        AuthenticationType authenticationType
        string authenticationConfig "never echoed back"
        Status status
    }
    TARGET_ENDPOINT {
        uuid id
        uuid target_id
        string key
        HttpVerb httpMethod
        string path
        int timeoutOverrideMs "declared, not yet applied"
    }
    TARGET_FIELD {
        uuid id
        uuid target_endpoint_id
        string fieldKey
        FieldDataType dataType
    }
    SUBSCRIPTION {
        uuid id
        uuid source_event_id
        uuid target_endpoint_id
        string targetPayloadTemplate "JSON template, JsonNode-tree mapping"
        string filterExpression "stored, not evaluated -- ADR-0005"
        int maxAttempts "nullable override"
        int initialBackoffMs "nullable override"
        int maxBackoffMs "nullable override"
        double backoffMultiplier "nullable override"
        boolean jitter "nullable override"
        int timeoutMs "nullable override"
    }
    EVENT {
        uuid id
        uuid source_event_id
        string resourceId
        Operation operation
        string idempotencyKey "partial-unique with source_event_id"
        string payload
    }
    DELIVERY {
        uuid id
        uuid event_id
        uuid subscription_id
        DeliveryState state
        int attemptCount
        timestamp nextAttemptAt "set only while RETRYING"
    }
    DELIVERY_ATTEMPT {
        uuid id
        uuid delivery_id
        int attemptNumber
        DeliveryStatus status
        int httpStatus
    }
```

Key changes from the pre-overhaul shape:

- **`TargetEndpoint`** (new, Stage 1) sits between `Target` and `TargetField`/`Subscription` — one
  Target (one system, one `baseUrl`) can expose several distinct API contracts (different
  method/path/request-shape), mirroring how `SourceEvent` already sat between `Source` and
  `SourceField`/`Subscription` on the other side.
- **`Subscription`** now references a `TargetEndpoint`, not a raw `(targetId, method, path)` triple
  — the endpoint itself *is* the API contract. It also gained the nullable DeliveryPolicy override
  fields (Stage 1 added the columns/API surface; Stage 2 wired them into the actual retry engine)
  and `filterExpression` (stored, not evaluated — see `docs/decisions/ADR-0005-subscription-filter-deferred.md`).
- **`AuthenticationType`** (new, Stage 1) is a typed enum alongside the pre-existing free-text
  `authenticationConfig` on both `Source` and `Target`. Only `NONE`/`API_KEY` are functionally wired
  and selectable in the console; `HMAC`/`OAUTH2`/`BEARER_TOKEN`/`BASIC` are declared so a later stage
  doesn't need another migration just to widen the set, but nothing reads them yet — the console
  deliberately does not offer them, so nothing in the UI looks functional without being so.
  `authenticationConfig` is a secret: the API never echoes it back once set (Stage 3 fixed a real
  bug here — see "Known gaps" below).
- **`Delivery.state`** gained `PROCESSING`/`RETRYING`/`REPLAYING` (Stage 2) alongside the original
  `PENDING`/`SUCCEEDED`/`DEAD` — see "Delivery / DLQ / Replay state machine" below.

## Source → Subscription → Target relationship

```mermaid
flowchart LR
    subgraph Source["Source: demo-flightstatus"]
        SE1["SourceEvent: flight-created\nPOST /ingress/v1/demo-flightstatus/flight-created"]
        SE2["SourceEvent: flight-status-updated\nPATCH /ingress/v1/demo-flightstatus/flight-status-updated"]
    end

    subgraph TargetA["Target: demo-airport-display"]
        TEA["TargetEndpoint: webhook\nPOST /targets/airport-display"]
    end

    subgraph TargetB["Target: demo-travelapp-vendor"]
        TEB["TargetEndpoint: webhook\nPOST /targets/travelapp-vendor"]
    end

    SE1 -- "Subscription\n(mapping + filter + delivery policy)" --> TEA
    SE1 -- "Subscription\n(different mapping: fewer fields)" --> TEB
    SE2 -- "Subscription" --> TEA
```

One `SourceEvent` can fan out to many `TargetEndpoint`s (each via its own `Subscription`, its own
mapping, and optionally its own delivery-policy override) and one `TargetEndpoint` can receive from
many `SourceEvent`s — the many-to-many join is `Subscription` itself, exactly as the demo scenario
above (`DemoDataSeeder`) exercises live in production.

## Event delivery flow

```mermaid
sequenceDiagram
    participant S as Source system
    participant I as IngressService
    participant O as Outbox / Kafka
    participant W as DeliveryWorker
    participant DS as DeliveryService
    participant RS as DeliveryRetryScheduler
    participant T as Target system

    S->>I: POST /ingress/v1/{source}/{event}
    I->>I: JSONPath extract + idempotency check (app fast-path + DB partial unique index)
    I->>I: persist Event + OutboxEvent (same transaction)
    I-->>S: 202 Accepted (or 200 if deduplicated)
    O->>W: Kafka delivery-task message (one per matching Subscription)
    W->>DS: deliver(event, subscription, payload)
    DS->>DS: map payload via targetPayloadTemplate (JsonNode tree, JSONPath)
    DS->>T: HTTP attempt 1 (synchronous, capped by effective timeoutMs)
    alt success
        DS-->>W: Delivery = SUCCEEDED
    else failure, attempts remain
        DS-->>W: Delivery = RETRYING (nextAttemptAt = now + backoff)
        Note over DS,RS: caller is released immediately -- no thread/connection held for the backoff wait
        RS->>DS: processDueRetry() once nextAttemptAt elapses
        DS->>T: HTTP attempt N
        alt success
            DS->>DS: Delivery = SUCCEEDED
        else attempts exhausted
            DS->>DS: Delivery = DEAD (DLQ)
        end
    end
```

## Delivery / DLQ / Replay state machine

```mermaid
stateDiagram-v2
    [*] --> PENDING: deliver() creates the Delivery
    PENDING --> PROCESSING: first attempt starts (near-instant)
    PROCESSING --> SUCCEEDED: HTTP 2xx
    PROCESSING --> RETRYING: failure, attempts remain
    RETRYING --> PROCESSING: DeliveryRetryScheduler picks it up once due
    PROCESSING --> DEAD: failure, attempts exhausted
    DEAD --> REPLAYING: manual Replay or DlqAutoReplayScheduler sweep
    REPLAYING --> SUCCEEDED: HTTP 2xx
    REPLAYING --> RETRYING: failure, attempts remain under the (possibly raised) effective maxAttempts
    REPLAYING --> DEAD: failure, attempts exhausted
    SUCCEEDED --> [*]
    DEAD --> [*]: until replayed
```

Notes on what changed in Stage 2 (the non-blocking retry engine):

- Before Stage 2, the entire multi-attempt retry loop ran synchronously inside one `Thread.sleep`-
  based call, holding the Kafka consumer thread and a DB connection for the whole backoff schedule.
  A crash mid-retry lost the rest of the schedule entirely.
- After Stage 2, only one HTTP attempt is ever in flight synchronously; `RETRYING` + `nextAttemptAt`
  is a durable, persisted wait state that survives a restart (`DeliveryRetryScheduler` resumes any
  due `RETRYING` delivery it finds, same as `DlqAutoReplayScheduler` already did for `DEAD`).
- `replay()` re-entering `RETRYING` (rather than always landing straight back on `DEAD`) is itself a
  Stage 2 behavior change: if `maxAttempts` was raised after a delivery went `DEAD`, a replay now
  actually benefits from the additional attempts instead of getting exactly one more try regardless.

Effective delivery policy per attempt (`DeliveryService.resolvePolicy`): each of `maxAttempts`,
`initialBackoffMs`, `maxBackoffMs`, `backoffMultiplier`, `jitter`, `timeoutMs` is read from the
Subscription's own nullable override if set, else a default — `maxAttempts` falls back to the
DB-configurable global default (`DeliverySettingsService`); the rest fall back to fixed constants
(200ms / 30s / 2.0x / no jitter / 10s), since no per-deployment global setting exists for those yet.
Backoff is exponential (`initialBackoffMs * backoffMultiplier^(attemptNumber-1)`, capped at
`maxBackoffMs`), optionally "equal jitter" (half fixed + half random) when `jitter` is enabled.

## Known gaps (deliberate, documented — not oversights)

- **Filter is stored, not evaluated.** `Subscription.filterExpression` has no evaluator; every
  active Subscription still delivers every matching Event regardless of its content. See
  `docs/decisions/ADR-0005-subscription-filter-deferred.md`. The console's Filter tab says so
  explicitly, not just this document.
- **`HMAC`/`OAUTH2`/`BEARER_TOKEN`/`BASIC` authentication types are declared but inert** — no
  signing, token-fetch flow, or credential injection exists for any of them (the console does not
  offer them as selectable, precisely so nothing looks functional without being so). Only `NONE`/
  `API_KEY` behave as their name implies today, and `API_KEY` itself is only ever stored, never
  actually attached to an outbound request — Source/Target authentication has never been
  functionally wired into the delivery path beyond storing a string.
- **`timeoutMs` is the only DeliveryPolicy field with per-request effect beyond backoff timing** —
  there is no circuit breaker, and `TargetEndpoint.timeoutOverrideMs` is declared but not read by
  `DeliveryService` (the Subscription-level `timeoutMs` override is what's actually applied).
- **A Target field registered under one `TargetEndpoint` is not validated against what
  `targetPayloadTemplate` actually references** — the registry is a mapping/autocomplete aid, not a
  hard constraint (same deliberate scope decision as Spec 006 made for the original flat model).
- **Secret handling is "never echo back," not full secret-manager integration** —
  `authenticationConfig` is a plain column, masked only by never being returned in an API response.
  Stage 3 fixed a real bug here: `SourceService.update`/`TargetService.update` used to overwrite the
  stored secret unconditionally, so saving the edit form with the (always-blank) field untouched
  silently cleared a working secret; blank/null now leaves the existing value alone.
