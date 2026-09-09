# ADR-0001: Repository-First Context

- Status: Accepted
- Date: 2026-09-10
- Deciders: Project maintainer

## Context

When an AI coding workflow depends on a large initialization prompt and previous conversations, project intent, design rationale, and working state are lost when the session or agent changes. Duplicating all context in agent-specific instruction files causes drift and binds core knowledge to particular tools.

RelayHub's own design context was originally captured in an external conversation and a set of exported documents. That origin cannot be assumed to remain available to future sessions, and the project must allow concise task prompts while outcomes still follow shared engineering principles and architecture. This project adopts [`agent-dev-starter`](https://github.com/cleanbrain-developer/agent-dev-starter)'s repository-first-context foundation for this reason.

## Decision

Use the repository as the authoritative source of persistent project context.

- Structure project identity in `PROJECT.yaml`.
- Store durable principles in `.ai/constitution/`.
- Store product and architecture explanations under `docs/`.
- Store significant decisions and rationale in ADRs.
- Store working state in `docs/status/current-state.md`.
- Store feature-level specification, contracts, plan, tasks, and verification in `specs/<feature>/`.
- Keep `CLAUDE.md` as a thin agent-specific adapter that loads the shared core.
- Remove the external design conversation/documents from long-term dependencies once their decisions are persisted in the repository (this ADR and the `docs/product/`, `docs/architecture/` documents it accompanies are that persistence step).

## Consequences

### Positive

- New sessions can continue work without the original design conversation.
- Shared policy has one source, reducing adapter drift.
- Decisions, working state, and task prompts have separate lifetimes and responsibilities.

### Costs and risks

- Code and documentation must be maintained together.
- An agent that ignores the bootstrap order may miss important context.
- Markdown rules cannot guarantee compliance; deterministic gates (tests, ArchUnit, CI) are needed as implementation lands.

## Alternatives considered

### Large reusable initialization prompt

Easy to start but creates oversized prompts and dependencies on conversations and tools. Rejected.

### Agent-specific documents as independent sources

Makes tool-specific optimization easy but creates policy duplication and drift. Rejected.
