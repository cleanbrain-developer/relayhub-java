# ADR-0006: No Ingress Rate Limiting (Yet) — a Deliberate, Documented Gap

- Status: Accepted
- Date: 2026-10-02
- Deciders: Project maintainer

## Context

A Source with `authenticationType=NONE` (every demo Source in this deployment today, and the
default for a newly-registered one) accepts ingress requests at `/ingress/v1/{sourceKey}/{eventKey}`
with no rate limiting whatsoever. The 2026-10-02 template-readiness self-review flagged this as a
real gap: this repository is meant to be the pattern future `relayhub-<lang>` sibling services are
stamped out from (see `docs/decisions/ADR-0002-java-spring-boot-stack.md`), and a silent absence of
rate limiting is the kind of thing that's easy to copy forward without ever having been a deliberate
choice.

This deployment's actual traffic (`relayhub-demo-systems`, a handful of requests every few seconds)
makes the absence of rate limiting a non-issue in practice today. That is not the same thing as it
being a reasoned decision, which is what this ADR records instead.

## Decision

**No rate limiting is added at this time.** This is an explicit, reasoned deferral, not an
oversight — recorded here specifically so a future reader (including a sibling-language
implementation) sees a decision, not a silent gap.

Two real mitigations already exist and are not nothing:

- A Source can be switched to `authenticationType=API_KEY` (see
  `common/ApiKeyAuth.java`/`IngressService.authenticate`), which at least requires a credential
  before any ingress request is accepted — not rate limiting, but it closes the "anyone on the
  internet can post arbitrary events" exposure for any Source an operator cares to protect.
- `GlobalExceptionHandler`/JSON Schema validation (`IngressService.validateSchema`) reject
  malformed payloads before they're persisted, bounding the cost of a single bad request even
  without a rate limiter.

## Consequences

### Positive

- No added complexity (a rate limiter needs a decision on scope — per-IP? per-Source? per-API-key?
  token bucket parameters? what happens on a shared NAT exit IP serving multiple legitimate
  callers?) for a risk this deployment doesn't currently have.
- The gap is now a documented, intentional decision instead of an invisible one — a sibling
  implementation can make its own informed choice instead of silently inheriting this one.

### Costs and risks

- A Source left at `authenticationType=NONE` (the default) has no protection at all against a
  request flood — not from a malicious actor, and not from an accidental one (e.g. a misconfigured
  real Source system retrying in a tight loop). At this project's demo-traffic scale that's
  survivable; it would not be at real production scale with a real, non-demo Source.
- This decision should be **revisited, not just re-read, the moment a real (non-demo) Source is
  ever connected** — the same trigger `domain-model.md`'s "Known gaps" already uses for revisiting
  the Delivery Attempt access policy and the authentication types that are declared but inert.

## Alternatives considered

### Add a basic per-IP rate limiter now (e.g. Bucket4j, or a hand-rolled token bucket)

Rejected for this stage — the actual rate-limiting *policy* (per-IP vs per-Source vs per-API-key,
what limits, what happens on a legitimate burst) is a product decision this ADR's context doesn't
have enough information to make well, and guessing at one now risks either being too strict for a
real Source's legitimate burst pattern or too permissive to matter. Revisit when a real Source's
actual traffic shape is known, not speculatively.

### Require `authenticationType=API_KEY` for every Source (remove the `NONE` option)

Rejected — this changes the registration model's own flexibility (a quick local test or a Source
behind its own network-level access control shouldn't be forced to manage an API key RelayHub
itself would need to protect), and doesn't actually rate-limit a request flood from a Source that
*does* have a valid key. Orthogonal to the rate-limiting question, not a substitute for it.
