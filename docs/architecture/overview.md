# Architecture Overview

## Architectural style

Modular Monolith. RelayHub starts as a single deployable Spring Boot application with clear internal module boundaries (see `system-design.md`), not a set of microservices. Distributed complexity is added only once a vertical slice proves it is necessary (see `docs/decisions/ADR-0001-repository-first-context.md`'s sibling ADRs and `docs/status/current-state.md`).

## Repository context layers (from Agent Development Starter)

```text
Task prompt
    v
Agent adapter (CLAUDE.md)
    v
PROJECT.yaml + Constitution + Product + Architecture + ADR + Current State
    v
Relevant repository evidence (including specs/)
    v
Plan -> Change -> Verify -> Review -> Persist state
```

- **Identity** — `PROJECT.yaml` exposes name, type, lifecycle, supported agents, principles, current phase, and canonical context paths.
- **Policy** — `.ai/constitution/` holds engineering and documentation principles that outlive an individual task.
- **Knowledge** — `docs/product/`, `docs/architecture/`, `docs/decisions/` explain what is being built, why, how it is structured, and why significant choices were made.
- **Feature specification** — `specs/<feature>/` holds the concrete spec, contracts, plan, tasks, and verification for one feature slice (see `repository-structure.md`).
- **Working state** — `docs/status/current-state.md` describes completed work and next actions for the current phase.
- **Adapter** — `CLAUDE.md` helps Claude Code discover shared context; it contains only tool-specific routing, never duplicated policy.

## Runtime architecture

See `system-design.md` for RelayHub's own system boundaries, domain modules, data flow, and the target Transactional Outbox / Kafka delivery pipeline.

## Deployment boundary

This repository owns RelayHub's application source, build (Gradle), and local Docker Compose environment. It does not own Kubernetes manifests for `cleanbrain.me` — those belong to `cleanbrain-me-infra` (planned hostname: `relayhub-java.developer.cleanbrain.me`, namespace `cleanbrain-me-relayhub-java`, once/if this service is deployed there). Do not add Kubernetes manifests to this repository.

## Enforcement boundary

Markdown guides judgment and procedure but does not enforce compliance. Rules that must never be violated should eventually be promoted to deterministic quality gates such as tests, linters, architecture checks (e.g. ArchUnit for module boundaries), and CI.
