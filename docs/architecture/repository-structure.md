# Repository Structure

## Responsibility map

| Path | Owner responsibility | Must not become |
|---|---|---|
| `README.md` | Human introduction and navigation | Full design specification |
| `PROJECT.yaml` | Structured project identity, phase, and pinned standard versions | Narrative architecture document |
| `AGENTS.md` | The sole bootstrap adapter and behavioral contract for every supported agent (`agent-dev-starter`'s `ADR-0011`) | Common policy source, or one of several duplicate adapters |
| `.specify/memory/constitution.md` | Durable engineering principles (GitHub Spec Kit's own constitution role — `agent-dev-starter`'s `ADR-0013`) | Project feature requirements |
| `.ai/constitution/documentation-policy.md` | Document ownership and the `.ko.md` language policy — no open standard owns this | Project feature requirements or engineering principles (those live in `.specify/memory/constitution.md`) |
| `.claude/skills/` | Real, agent-discovered Skill directories, once a maintainer describes a real workflow (`agent-dev-starter`'s `ADR-0003`/`ADR-0012`) | `.ai/skills/` or any other invented path Claude Code does not actually read |
| `docs/product/` | Problem, users, goals, scope | Implementation instructions |
| `docs/architecture/` | Structure, boundaries, context model, system design | Decision history |
| `docs/decisions/` | Important decisions and rationale | Mutable current-state checklist |
| `docs/status/current-state.md` | Current phase, progress, next work | Permanent policy or changelog |
| `specs/<feature>/` | One feature's spec, contracts, plan, tasks, verification | Cross-feature policy or long-lived architecture |
| `src/` | Application source (Java/Spring Boot) | Documentation or specs |
| `scripts/` | Repeatable verification/smoke-test scripts | Ad hoc one-off scripts |

## Top-level layout

```text
relayhub-java/
├── README.md
├── AGENTS.md
├── PROJECT.yaml
├── build.gradle.kts
├── settings.gradle.kts
├── docker-compose.yml
├── .ai/
│   └── constitution/
│       └── documentation-policy.md
├── .specify/
│   └── memory/
│       └── constitution.md
├── docs/
│   ├── product/
│   ├── architecture/
│   └── decisions/
├── docs/status/current-state.md
├── specs/
│   └── 001-push-event-delivery/
│       ├── spec.md
│       ├── contracts.md
│       ├── plan.md
│       ├── tasks.md
│       └── verification.md
├── src/
│   ├── main/
│   └── test/
├── scripts/
│   ├── verify.sh
│   └── compose-smoke-test.sh
└── .github/
    └── workflows/
```

`specs/001-push-event-delivery/` through `specs/006-field-registry/` are this project's own pre-Spec-Kit feature tree, predating GitHub Spec Kit adoption; they have not yet been migrated into Spec Kit's own `specs/<NNN-feature>/` structure (pending a real `specify init` run — see `docs/status/current-state.md`, "Known constraints"). `.specify/` otherwise belongs to the pinned Spec Kit CLI once actually installed, not something this repository hand-forks.

## Dependency direction

Adapters, and this repository's own README/specs, may point inward to authoritative documents. Authoritative documents (`PROJECT.yaml`, `.specify/memory/constitution.md`, `.ai/constitution/documentation-policy.md`, `docs/product/`, `docs/architecture/`, accepted ADRs) do not depend on adapter wording or external conversation history — including the original design document this project was bootstrapped from.

```text
README ───────────────┐
AGENTS.md ────────────┼──> PROJECT.yaml + .specify/memory/constitution.md + docs/product + docs/architecture
current-state ────────┘                 │
specs/<feature> ─────────────────────────┼──> accepted ADRs
```

## Directory policy

Add a directory only when a file has a real responsibility within it. Do not create empty structures for undefined modules, presets, or scripts before Phase 1 needs them.

## Evolution rule

Before adding a top-level area or responsibility layer, confirm that an existing location cannot represent it. A change to the long-term dependency direction, module boundaries, or the modular-monolith-first decision requires an ADR.
