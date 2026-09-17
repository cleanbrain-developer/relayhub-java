> 이 문서는 [`repository-structure.md`](repository-structure.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Repository Structure

## Responsibility map

| Path | Owner responsibility | Must not become |
|---|---|---|
| `README.md` | 사람을 위한 소개와 navigation | 완전한 design specification |
| `PROJECT.yaml` | Structured project identity와 phase | 서술형 architecture document |
| `CLAUDE.md` | Claude Code bootstrap adapter | 공유 policy의 중복 |
| `.ai/constitution/` | Durable engineering 및 agent principle | Project feature requirement |
| `docs/product/` | Problem, users, goals, scope | Implementation instruction |
| `docs/architecture/` | Structure, boundaries, context model, system design | Decision history |
| `docs/decisions/` | 중요한 decision과 rationale | 변경 가능한 current-state checklist |
| `docs/status/current-state.md` | 현재 phase, progress, next work | 영구적인 policy나 changelog |
| `specs/<feature>/` | 하나의 feature에 대한 spec, contract, plan, task, verification | Cross-feature policy나 장기 architecture |
| `src/` | Application source (Java/Spring Boot) | Documentation나 spec |
| `scripts/` | 반복 가능한 verification/smoke-test script | Ad hoc한 일회성 script |

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

`build.gradle.kts`, `settings.gradle.kts`, `docker-compose.yml`, `src/`, `scripts/`, `.github/workflows/`는 Phase 1 implementation이 시작될 때 생성됩니다. 현재 `v1-foundation` phase에는 아직 존재하지 않습니다(`docs/status/current-state.md` 참고).

## Dependency direction

Adapter와 이 repository 자체의 README/spec은 authoritative document를 향해 안쪽으로 가리킬 수 있습니다. Authoritative document(`PROJECT.yaml`, `.ai/constitution/`, `docs/product/`, `docs/architecture/`, accepted ADR)는 adapter의 문구나 external conversation history — 이 project가 bootstrap된 원래의 design document를 포함하여 — 에 의존하지 않습니다.

```text
README ───────────────┐
CLAUDE.md ────────────┼──> PROJECT.yaml + constitution + docs/product + docs/architecture
current-state ────────┘                 │
specs/<feature> ─────────────────────────┼──> accepted ADRs
```

## Directory policy

파일이 그 안에서 실제 책임을 가질 때만 디렉터리를 추가하세요. Phase 1이 필요로 하기 전에 정의되지 않은 module, preset, script를 위한 빈 구조를 만들지 마세요.

## Evolution rule

top-level area나 responsibility layer를 추가하기 전에, 기존 위치로 그것을 표현할 수 없는지 확인하세요. 장기적인 dependency direction, module boundary, 또는 modular-monolith-first decision에 대한 변경은 ADR을 필요로 합니다.
