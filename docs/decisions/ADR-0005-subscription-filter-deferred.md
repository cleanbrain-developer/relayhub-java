# ADR-0005: Subscription Filter — Store, Don't Evaluate (Yet)

- Status: Accepted
- Date: 2026-09-30
- Deciders: Project maintainer

## Context

The integration-platform domain-model overhaul (maintainer request 2026-09-30) asked for a Filter
concept on Subscription — e.g. only deliver when `status == "DELAYED"` or `delayMinutes > 0` — so a
Subscription can narrow which Canonical Events it actually forwards, not just map every event that
reaches it. The same request explicitly said not to build a full rule engine for this if it would
be disproportionate for the current stage, and to leave a documented extension point instead.

Building a real expression evaluator (parser, safe execution sandbox, type coercion rules matching
the Canonical Event's JSON shape, UI to author/validate it) is a meaningfully larger scope than
everything else in this stage — closer to a small project of its own than an incidental addition.

## Decision

`Subscription.filterExpression` is added as a plain nullable text column and exposed through the
create/update/response API and (later, Stage 3) the console UI as a free-text field. **Nothing
evaluates it.** `IngressService`/`DeliveryService` queue and attempt every active Subscription's
delivery exactly as before this column existed, regardless of what `filterExpression` contains.

## Consequences

### Positive

- The domain model and API shape are already correct for when evaluation is built — no future
  migration needed just to add the concept.
- An operator can start recording intent ("this Subscription is meant to only fire on delayed
  flights") even before enforcement exists, which is useful documentation on its own.

### Costs and risks

- A filter expression that looks configured does nothing — the UI must make this unambiguous (a
  visible "not yet enforced" note, not a checkbox that implies it's active) so an operator doesn't
  reasonably assume traffic is actually being filtered. This is a real footgun if the UI ever
  presents it as more than a text note, and needs explicit handling in Stage 3.
- No validation of the expression syntax happens anywhere yet — a typo is silently harmless today,
  but would need surfacing once evaluation exists.

## Alternatives considered

### Build a minimal expression evaluator now (e.g. a small subset: `field op literal`)

Rejected for this stage — even a "minimal" evaluator needs a safe parser, a decision on how it
reads Canonical Event fields (JSONPath again? a flattened view?), type coercion rules, and error
handling for a malformed expression at delivery time (fail the delivery? skip silently? log and
deliver anyway?). Each of those is a real design decision the original request didn't ask this
stage to make, and getting any of them wrong is worse than not having the feature yet.

### Skip the column entirely until evaluation is built

Rejected — the maintainer's request specifically asked for the domain model and an extension point
to exist now, not just a future design note with no code trace.
