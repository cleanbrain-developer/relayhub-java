# Spec 006: Source/Target Field Registry (proposal — not yet implemented)

## Problem

Subscription creation/editing (`MappingBuilder`, Spec 005 follow-up) improved the mapping *UI*
but not the underlying data: a Subscription's `targetPayloadTemplate` mapping rows are still free
text — an operator has to already know the exact source JSONPath (`$.customerNo`) and the exact
target field name (`dealerId`) from memory or external documentation. Nothing validates a mapping
against what the Source Event actually emits or what the Target actually accepts, and nothing is
reusable across Subscriptions to the same Source Event or Target.

Maintainer's own framing: Source and Target should each register *every field they can supply or
need*, and Subscription mapping should become a selection (dropdown) from those two registries
instead of free-typed text.

## Proposed design

### Two new entities

**`SourceField`** — one row per field a SourceEvent's payload can supply. Scoped to the
**SourceEvent**, not the bare Source: two events on the same Source (e.g. `flight-created` vs.
`flight-status-updated`) can have different payload shapes, and a field's JSONPath is only
meaningful relative to one event's payload.

**`TargetField`** — one row per field a Target's payload can accept. Scoped to the **Target**
itself (not per-endpoint/path) for v1 — Subscription already carries its own `targetPath` per
mapping, and introducing a separate "Target endpoint" concept on top of that felt like scope this
spec doesn't need yet. If a Target's different endpoints genuinely need different field sets, that
can be split out later without disturbing Subscriptions already using this registry.

### Proposed metadata per field (the part asking for feedback)

| Field | Type | Notes |
|---|---|---|
| `key` | string, required | Short field name shown in the mapping UI (e.g. `customerNo`, `dealerId`) |
| `jsonPath` | string, **SourceField only**, required | Extraction path against the raw Source payload (e.g. `$.customerNo`) |
| `dataType` | enum: `STRING`/`NUMBER`/`BOOLEAN`/`OBJECT`/`ARRAY`/`DATE` | For UI hints and light validation — not a full JSON Schema replacement |
| `description` | string, optional | Human-readable explanation |
| `exampleValue` | string, optional | Sample value shown in the UI so an operator recognizes the field without reading the Source's own docs |
| `required` | boolean | SourceField: "the Source always sends this." TargetField: "the Target rejects the request without this." |
| `sensitive` | boolean | PII/secret marker — no behavior yet (no masking/redaction implemented), but worth capturing now so it doesn't need a migration later if that's ever built |
| `status` | enum: `ACTIVE`/`INACTIVE` | Same soft-delete convention as every other entity here |
| `createdAt` | timestamp | Standard |

Open question for feedback: is this the right set? Candidates considered and deliberately left
out unless you want them: a `defaultValue` for `TargetField` (value to send when nothing is
mapped to it), and Source-side "this field is commonly used as occurredAtPath/idempotencyKeyPath"
hint flags — both felt like premature complexity for a first version.

### Subscription mapping becomes a selection, not free text

`MappingBuilder`'s row-based editor stays the same shape, but each row becomes: pick a
`TargetField` (from the selected Target's registry) on the left, pick a `SourceField` (from the
selected SourceEvent's registry) on the right — or fall back to a literal value / raw JSONPath for
anything not yet registered (same "advanced" escape hatch `MappingBuilder` already has). On
submit, the frontend still generates the exact same `targetPayloadTemplate` JSON string the
backend already expects (`{"<targetField.key>": "${<sourceField.jsonPath>}"}`) — **no change to
`MappingService`, `DeliveryService`, or the Subscription API's request/response shape.** This is
additive: a smarter data source for the same UI, not a new mapping engine.

### New endpoints (all following the existing list/create/edit/soft-delete pattern from Spec 005)

- `GET/POST/PUT/DELETE /api/sources/{sourceKey}/events/{eventKey}/fields(/{fieldKey})`
- `GET/POST/PUT/DELETE /api/targets/{targetKey}/fields(/{fieldKey})`

Both nested the same way `SourceEvent` nests under `Source` today. Writes protected by the
existing admin auth boundary; reads public, same as everything else.

## Deliberately out of scope (for now)

- Enforcing/validating that a Subscription's mapping only uses registered fields — registries
  start as documentation/autocomplete aids, not a hard constraint. (Could become one later.)
  Removing this decision from scope also avoids a migration problem for the existing seeded demo
  Subscriptions, which predate any field registry.
  - Auto-populating the registry from an actual observed Source payload (schema inference) — an
  operator registers fields by hand for v1, same effort level as the current free-text approach,
  just reusable afterward.
- Per-endpoint `TargetField` scoping (see "TargetField" above).
- Anything for the `sensitive` flag beyond storing it.

## Status

Implemented and deployed (2026-09-12). Backend: `SourceField`/`TargetField` entities, `V2__field_registry.sql`, nested REST controllers under `/api/sources/{sourceKey}/events/{eventKey}/fields` and `/api/targets/{targetKey}/fields`, following the same list/create/edit/soft-delete(+admin hard-delete) contract as every other entity here. Frontend: `FieldRegistryEditor` (full add/edit/delete) surfaced via a "Field mapping" toggle on Sources/Targets cards, and `MappingBuilder`'s two sides became dropdowns sourced from the registries when populated, falling back to free text otherwise — exactly as designed above, no changes to `MappingService`/`DeliveryService`/the Subscription API contract. `DemoDataSeeder` also seeds example fields for the demo scenario so a fresh environment isn't an empty registry.
