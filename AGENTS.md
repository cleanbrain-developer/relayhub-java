# AGENTS.md

This file is the single entry point for any coding agent working in this repository. It is not the source of truth for product, architecture, or principles; it routes to them and states the behavioral contract every agent follows here. This project currently supports Claude Code only (see `PROJECT.yaml`'s `supported_agents`), but this file follows the agent-agnostic `AGENTS.md` convention rather than a Claude-specific one, per `agent-dev-starter`'s `ADR-0011` — a prior separate `CLAUDE.md` adapter has been merged into this file and removed.

## Context bootstrap

Read `PROJECT.yaml` first, then follow the bootstrap order and conflict-handling rules defined in `docs/architecture/agent-context-model.md`. This file does not restate that order — if this section and that document ever disagree, `agent-context-model.md` wins.

## Working contract

### Before work

- Load persistent context in the order `agent-context-model.md` defines.
- Inspect the current request, `docs/status/current-state.md`, and relevant files.
- Find evidence in existing documentation and implementation before proposing a change, then determine the affected scope.
- For change work, prepare a proportionate plan and verification strategy before implementation.

### During work

- Stay within the user request and accepted decisions; treat existing changes as user-owned and do not overwrite unrelated work.
- Surface material assumptions and architectural changes instead of deciding them silently.
- Record a new rule once, in its correct source of truth — never in this file.
- External contract flexibility outranks introducing a RelayHub-specific payload envelope; reliability outranks feature count. See `.specify/memory/constitution.md` and `docs/product/goals.md`.

### Before completion

- Run relevant verification or state why it could not be run; distinguish a green automated check from direct verification against the real running system.
- Review the final change against the requirement, architecture, and documentation responsibilities.
- Distinguish completed work, remaining risks, and open decisions.
- Update `docs/status/current-state.md` when the next session needs to know about a state change.
- If this change edited a document with a `.ko.md` companion, update the companion in the same change (`agent-dev-starter`'s `ADR-0005`) — a stale translation is a defect, not a follow-up task.

Claiming completion and demonstrating verification are different. Do not claim success without verification evidence. For RelayHub specifically, this includes: `./gradlew test` passing, Docker Compose starting cleanly, integration/contract tests passing, and a reproducible demo scenario — see `docs/product/goals.md` and `specs/001-push-event-delivery/verification.md`.

## Conflict handling

- A user request defines the work objective but does not silently discard accepted architecture.
- A specific accepted ADR takes precedence over a general architecture description.
- `current-state.md` does not redefine principles or design.
- A `specs/<feature>/` document does not redefine product or architecture decisions; it implements them.
- Report unresolved conflicts instead of hiding them behind assumptions.

Use `docs/status/current-state.md` as the source of truth for the current phase and next work.
