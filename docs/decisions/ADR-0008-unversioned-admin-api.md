# ADR-0008: The Admin API (`/api/**`) Stays Unversioned — a Deliberate, Documented Gap

- Status: Accepted
- Date: 2026-10-06
- Deciders: Project maintainer

## Context

The 2026-10-06 scale-out readiness review noted an inconsistency relevant to the
`relayhub-<lang>` sibling-repository goal (`docs/architecture/porting-guide.md`): the
Source-facing ingestion contract carries an explicit version segment
(`/ingress/v1/{sourceKey}/{eventKey}`), but the admin console's own API
(`/api/sources`, `/api/targets`, `/api/deliveries`, etc.) carries no version prefix at all. If a
`relayhub-<lang>` sibling's admin API shape ever needs to diverge from this one — a different
field, a different response envelope — there's no existing convention for whether that's a
breaking change against *this* contract or an acceptable implementation difference.

## Decision

**`/api/**` stays unversioned.** Only `/ingress/v1/**` carries a version segment, and that
decision stands for a reason specific to that endpoint: it's the one contract an *external,
independently-deployed* system (a real Source) depends on, where RelayHub cannot coordinate a
simultaneous upgrade on both sides. `/api/**` is consumed exclusively by this repository's own
frontend, built and deployed from the same repository, at the same time, by the same CI pipeline
(see `.github/workflows/ci.yml`'s `build-and-push` → `deploy` sequence) — frontend and backend
never run at different versions against each other in production. There is no real compatibility
problem `/api/**` versioning would solve today.

For the `relayhub-<lang>` porting question this ADR exists to answer: a sibling implementation's
admin API is **not** required to byte-for-byte match this one. `docs/architecture/api-contract.md`
and the generated OpenAPI spec (`/v3/api-docs`) describe *this* implementation's current shape as
a reference, not a frozen cross-language contract — only the `/ingress/v1/**` convention and the
underlying domain model (`docs/architecture/domain-model.md`) are the actual product design a
sibling must replicate exactly, per `ADR-0002`'s original replicate-exactly-vs-port-idiomatically
split.

## Consequences

### Positive

- No speculative versioning scheme (`/api/v1/**`? header-based? content-negotiation-based?) for a
  compatibility problem that doesn't exist — frontend and backend are always deployed together.
- Avoids a confusing asymmetry where `/api/**` carries a version number that never actually changes
  (a `v1` nobody has ever needed to bump is worse than no version marker, implying a stability
  contract that isn't real).

### Costs and risks

- If `/api/**` is ever consumed by something other than this repository's own frontend — a CLI
  tool, a third-party integration, a sibling console reusing this backend — this decision would
  need revisiting before that integration could safely track a moving target.
- A `relayhub-<lang>` sibling's admin API shape drifting from this one is expected and fine per
  the decision above, but means `api-contract.md` cannot be assumed identical across siblings —
  each sibling's own generated OpenAPI spec is its actual source of truth, not this document.

## Alternatives considered

### Add `/api/v1/**` now, even with only one version ever existing

Rejected — see "Costs and risks" above: an unused version marker implies a stability guarantee
this project has never actually needed to keep, and would need every existing frontend call site
updated for no present benefit.

### Require every `relayhub-<lang>` sibling's admin API to match this one exactly

Rejected — this would turn an internal implementation detail (how this specific Java/Spring/React
stack happens to shape its own CRUD endpoints) into a cross-language contract, which `ADR-0002`'s
replicate-exactly-vs-port-idiomatically split already says the admin API is not: only the domain
model and the ingress contract are the real product design to replicate.

## Revisit trigger

Something other than this repository's own frontend needs to consume `/api/**` and depend on its
shape staying stable across a deploy — the same "a real need shows up" pattern `ADR-0005`/`ADR-0006`/
`ADR-0007` already use.
