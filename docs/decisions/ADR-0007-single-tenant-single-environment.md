# ADR-0007: Single-Tenant, Single-Environment Console — a Deliberate, Documented Gap

- Status: Accepted
- Date: 2026-10-06
- Deciders: Project maintainer

## Context

The 2026-10-06 scale-out readiness review (ahead of stamping out `relayhub-<lang>` sibling
services from this template — see `docs/architecture/porting-guide.md`) confirmed there is no
tenant concept anywhere in this codebase: no `tenantId`/`organizationId`/`accountId` field on any
entity, controller, or frontend type, and exactly one fixed admin account
(`relayhub.admin.username`/`relayhub.admin.password`, see `SecurityConfig.java`). There is also no
environment concept in the console itself — Spring profiles (`dev`/`test`/`demo`/production)
select *configuration*, but nothing in the UI or API lets an operator see or switch between
"staging" and "production" data within one running instance.

Before scaling out, this needs to be a decision, not an unexamined default: does "RelayHub" mean
*one instance per team/service that integrates it* (today's actual shape — one Hetzner deployment,
one admin account, one Postgres database, serving one set of demo integrations), or *one shared
instance that many teams register their own Sources/Targets/Subscriptions into*? The two imply
very different domain models — the latter needs a `tenantId` on nearly every entity, per-tenant
admin accounts, and data isolation enforced at the query layer, none of which exists today.

## Decision

**RelayHub stays single-tenant, single-environment per deployed instance.** Scaling out to more
integrations or more services means deploying more RelayHub instances (one per team/service,
matching the `relayhub-<lang>` sibling-repository model `ADR-0002` already established), not
adding multi-tenancy to one shared instance.

This is the natural fit for the project's actual scale (`cleanbrain-me-infra`'s 2 vCPU/4 GB/40 GB
Hetzner box, a personal-project deployment target, not a SaaS platform) and for what's already
built: the admin console, auth model, and domain model all already assume "one operator, one set
of integrations" — retrofitting tenant isolation onto that would touch nearly every entity and
controller for a need that doesn't exist yet.

## Consequences

### Positive

- No speculative schema/auth rework for a requirement that isn't real yet — consistent with this
  project's existing discipline of not building ahead of actual need (see ADR-0005, ADR-0006).
- The `relayhub-<lang>` porting guide's domain model stays simple to replicate: a sibling
  implementation doesn't need to reason about tenant scoping at all.
- Clear operational model: one Kubernetes namespace, one database, one admin account per
  deployment — matches how `cleanbrain-me-infra`'s other services are already deployed.

### Costs and risks

- If RelayHub is ever asked to serve multiple independent teams/services from one instance, this
  is a real, invasive migration later (tenant-scoping every entity, every query, every API
  response) rather than something that can be bolted on incrementally.
- Multiple real operators sharing one instance today would have to share the single admin
  credential — there is no per-operator audit trail or access scoping. Acceptable at today's
  single-operator scale; revisit if that changes.

## Alternatives considered

### Add a `tenantId` column now, even if unused, to avoid a later migration

Rejected — an unused column with no enforcement anywhere is worse than no column: it looks like
tenant isolation exists when it doesn't, which is more dangerous than an honest absence. If
multi-tenancy is ever needed, it should be designed against real requirements (how are tenants
created? by whom? what's shared vs. isolated — Source/Target registries, or also delivery
history?), not guessed at speculatively now.

### Multi-tenant from the start of the next `relayhub-<lang>` sibling

Rejected for the same reason `ADR-0002` ties sibling repositories to replicating this project's
*current* domain model, not a hypothetical future one — a sibling should port what's actually
proven here, not a speculative redesign that hasn't shipped anywhere yet.

## Revisit trigger

The moment a second real (non-demo) team or service genuinely needs to share one RelayHub
instance rather than getting its own deployment — the same "a real need shows up" trigger
`ADR-0005`/`ADR-0006` already use for their own deferrals.
