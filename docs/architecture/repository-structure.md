# Repository Structure

## Responsibility map

| Path | Owner responsibility | Must not become |
|---|---|---|
| `README.md` | Human introduction and navigation | Full design specification |
| `PROJECT.yaml` | Structured project identity and phase | Narrative architecture document |
| `CLAUDE.md` | Claude Code bootstrap adapter | Duplicate of shared policy |
| `.ai/constitution/` | Durable engineering and agent principles | Project feature requirements |
| `docs/product/` | Problem, users, goals, scope | Implementation instructions |
| `docs/architecture/` | Structure, boundaries, context model, system design | Decision history |
| `docs/decisions/` | Important decisions and rationale | Mutable current-state checklist |
| `docs/status/current-state.md` | Current phase, progress, next work | Permanent policy or changelog |
| `specs/<feature>/` | One feature's spec, contracts, plan, tasks, verification | Cross-feature policy or long-lived architecture |
| `src/` | Application source (Java/Spring Boot) | Documentation or specs |
| `scripts/` | Repeatable verification/smoke-test scripts | Ad hoc one-off scripts |

## Planned top-level layout

```text
relayhub-java/
├── README.md
├── CLAUDE.md
├── PROJECT.yaml
├── build.gradle.kts
├── settings.gradle.kts
├── docker-compose.yml
├── .ai/
│   └── constitution/
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

`build.gradle.kts`, `settings.gradle.kts`, `docker-compose.yml`, `src/`, `scripts/`, and `.github/workflows/` are created when Phase 1 implementation begins; they do not exist yet in the current `v1-foundation` phase (see `docs/status/current-state.md`).

## Dependency direction

Adapters, and this repository's own README/specs, may point inward to authoritative documents. Authoritative documents (`PROJECT.yaml`, `.ai/constitution/`, `docs/product/`, `docs/architecture/`, accepted ADRs) do not depend on adapter wording or external conversation history — including the original design document this project was bootstrapped from.

```text
README ───────────────┐
CLAUDE.md ────────────┼──> PROJECT.yaml + constitution + docs/product + docs/architecture
current-state ────────┘                 │
specs/<feature> ─────────────────────────┼──> accepted ADRs
```

## Directory policy

Add a directory only when a file has a real responsibility within it. Do not create empty structures for undefined modules, presets, or scripts before Phase 1 needs them.

## Evolution rule

Before adding a top-level area or responsibility layer, confirm that an existing location cannot represent it. A change to the long-term dependency direction, module boundaries, or the modular-monolith-first decision requires an ADR.
