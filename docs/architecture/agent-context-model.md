# Agent Context Model

Adopted from Agent Development Starter without project-specific changes.

## Context classes

### Permanent context

Information that persists across sessions, including project purpose, scope, architecture, engineering principles, and accepted decisions. Store it in `PROJECT.yaml`, `.ai/constitution/`, product and architecture documents, and ADRs.

### Working context

Information that changes with progress, including the current phase, recently completed work, next actions, and open decisions. Store it in `docs/status/current-state.md`.

### Feature context

A single feature's specification, contracts, plan, tasks, and verification. Store it in `specs/<feature>/`. It depends on permanent context; permanent context does not depend on it.

### Task context

The current user request, relevant code, and temporary research findings. Load it only when needed. If it gains durable value, persist it in the appropriate permanent, working, or feature source.

## Bootstrap order

1. Start from `CLAUDE.md`.
2. Read `PROJECT.yaml` to identify the project and phase.
3. Read the constitution (`.ai/constitution/`) to understand behavioral boundaries.
4. Read product documents (`docs/product/`) to understand purpose and scope.
5. Read architecture documents (`docs/architecture/`) to understand structure and responsibilities.
6. Read accepted ADRs (`docs/decisions/`) relevant to the current work.
7. Read the relevant `specs/<feature>/` directory for feature-level detail.
8. Read `docs/status/current-state.md` to recover the current position and next work.
9. Inspect repository evidence relevant to the request.
10. Plan, change, verify, and review.
11. Persist durable decisions and working-state changes in the repository.

## Conflict handling

- A user request defines the work objective but does not silently discard accepted architecture.
- A specific accepted ADR takes precedence over a general architecture description.
- Current state does not redefine principles or design.
- A `specs/<feature>/` document does not redefine product or architecture decisions; it implements them.
- Report unresolved conflicts instead of hiding them behind assumptions.

## Bootstrap acceptance test

In a clean session, provide only `CLAUDE.md` and no external link. Context recovery succeeds when the agent answers the five acceptance questions in `docs/product/goals.md` ("Success criteria"), with every answer traceable to repository documentation.
