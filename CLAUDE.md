# Claude Code Project Adapter

This file is the repository entry adapter for Claude Code. It is not the source of truth for shared policy or project design; it routes Claude Code to the repository context below.

## Required context

Read `PROJECT.yaml` first, then follow the bootstrap order and conflict-handling rules defined in `docs/architecture/agent-context-model.md`. This adapter does not restate that order — if this list and that document ever disagree, `agent-context-model.md` wins.

## Working contract

- Inspect existing evidence and prepare a change plan.
- Implement the smallest coherent change, verify it, and review the final diff.
- Do not leave architectural decisions or durable state only in conversation history.
- Add shared rules to the appropriate constitution or documentation source, not to this adapter.
- Make unresolved conflicts and open decisions explicit instead of silently fixing them through assumptions.
- External contract flexibility outranks introducing a RelayHub-specific payload envelope; reliability outranks feature count. See `.ai/constitution/engineering-principles.md` and `docs/product/goals.md`.

Use `docs/status/current-state.md` as the source of truth for the current phase and next work.
