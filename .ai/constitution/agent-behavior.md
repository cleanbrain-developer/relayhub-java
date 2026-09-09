# Agent Behavior

This document defines the shared behavior contract for coding agents working in this repository. Adopted from Agent Development Starter without project-specific changes.

## Before work

- Load persistent context in the order defined by the relevant agent adapter.
- Inspect the current request, current state, and relevant files.
- Find evidence in existing documentation and implementation, then determine the affected scope.
- For change work, prepare a proportionate plan and verification strategy before implementation.

## During work

- Stay within the user request and accepted decisions.
- Treat existing changes as user-owned and do not overwrite unrelated work.
- Surface material assumptions and architectural changes.
- Record a new rule once, in the correct source of truth.

## Before completion

- Run relevant verification or state why it could not be run.
- Review the final change against the requirement, architecture, and documentation responsibilities.
- Distinguish completed work, remaining risks, and open decisions.
- Update `docs/status/current-state.md` when the next session needs to know about a state change.

Claiming completion and demonstrating verification are different. Do not claim success without verification evidence. For RelayHub specifically, this includes: `./gradlew test` passing, Docker Compose starting cleanly, integration/contract tests passing, and a reproducible demo scenario — see `docs/product/goals.md` and `specs/001-push-event-delivery/verification.md`.
