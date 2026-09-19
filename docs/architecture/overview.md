# Architecture Overview

## Architectural style

Modular Monolith. RelayHub starts as a single deployable Spring Boot application with clear internal module boundaries (see `system-design.md`), not a set of microservices. Distributed complexity is added only once a vertical slice proves it is necessary (see `docs/decisions/ADR-0001-repository-first-context.md`'s sibling ADRs and `docs/status/current-state.md`).

## Repository context layers (from Agent Development Starter V2 — AGENTS.md, GitHub Spec Kit, Agent Skills)

```text
Task prompt
    v
AGENTS.md (sole adapter, agent-dev-starter's ADR-0011)
    v
PROJECT.yaml + .specify/memory/constitution.md + Product + Architecture + ADR + Current State
    v
Relevant repository evidence (including specs/)
    v
Plan -> Change -> Verify -> Review -> Persist state
```

- **Identity** — `PROJECT.yaml` exposes name, type, lifecycle, supported agents, principles, current phase, pinned standard versions, and canonical context paths.
- **Policy** — `.specify/memory/constitution.md` (GitHub Spec Kit's own constitution role) holds durable engineering principles that outlive an individual task. `.ai/constitution/documentation-policy.md` holds document ownership and the `.ko.md` language policy — the one policy area no open standard owns.
- **Knowledge** — `docs/product/`, `docs/architecture/`, `docs/decisions/` explain what is being built, why, how it is structured, and why significant choices were made.
- **Feature specification** — `specs/<feature>/` holds the concrete spec, contracts, plan, tasks, and verification for one feature slice (see `repository-structure.md`). This tree predates this project's adoption of GitHub Spec Kit and has not yet been migrated into Spec Kit's own `specs/<NNN-feature>/` structure — see `docs/status/current-state.md`, "Known constraints".
- **Working state** — `docs/status/current-state.md` describes completed work and next actions for the current phase.
- **Adapter** — `AGENTS.md` is the sole entry point for every supported agent (currently Claude Code only — see `PROJECT.yaml`). It contains only routing and the shared behavioral contract, never product or architecture content.
- **Skills** — `.claude/skills/` holds reusable procedures Claude Code's own harness discovers automatically. None exist in this repository yet; a project-specific skill is added only once a maintainer describes a real, repeatable workflow (see `agent-dev-starter`'s `ADR-0003`/`ADR-0012`).

## Runtime architecture

See `system-design.md` for RelayHub's own system boundaries, domain modules, data flow, and the target Transactional Outbox / Kafka delivery pipeline.

## Deployment boundary

This repository owns RelayHub's application source, build (Gradle), and local Docker Compose environment. It does not own Kubernetes manifests for `cleanbrain.me` — those belong to `cleanbrain-me-infra` (planned hostname: `relayhub-java.developer.cleanbrain.me`, namespace `cleanbrain-me-relayhub-java`, once/if this service is deployed there). Do not add Kubernetes manifests to this repository.

## Enforcement boundary

Markdown guides judgment and procedure but does not enforce compliance. Rules that must never be violated should eventually be promoted to deterministic quality gates such as tests, linters, architecture checks (e.g. ArchUnit for module boundaries), and CI.
