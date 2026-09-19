> 이 문서는 [`repository-structure.md`](repository-structure.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Repository Structure

## Responsibility map

| Path | Owner responsibility | Must not become |
|---|---|---|
| `README.md` | 사람을 위한 소개와 navigation | 완전한 design specification |
| `PROJECT.yaml` | Structured project identity, phase, pinned standard version | 서술형 architecture document |
| `AGENTS.md` | 모든 지원 agent를 위한 sole bootstrap adapter와 behavioral contract(`agent-dev-starter`의 `ADR-0011`) | 공유 policy source, 또는 여러 개의 중복 adapter 중 하나 |
| `.specify/memory/constitution.md` | Durable engineering principle (GitHub Spec Kit 자체의 constitution 역할 — `agent-dev-starter`의 `ADR-0013`) | Project feature requirement |
| `.ai/constitution/documentation-policy.md` | Document ownership과 `.ko.md` language policy — 어떤 open standard도 이것을 소유하지 않음 | Project feature requirement이나 engineering principle(그것들은 `.specify/memory/constitution.md`에 있음) |
| `.claude/skills/` | maintainer가 실제 workflow를 설명한 이후의, 실제로 agent가 discover하는 Skill directory(`agent-dev-starter`의 `ADR-0003`/`ADR-0012`) | Claude Code가 실제로 읽지 않는 `.ai/skills/`나 그 밖의 임의의 path |
| `docs/product/` | Problem, users, goals, scope | Implementation instruction |
| `docs/architecture/` | Structure, boundaries, context model, system design | Decision history |
| `docs/decisions/` | 중요한 decision과 rationale | 변경 가능한 current-state checklist |
| `docs/status/current-state.md` | 현재 phase, progress, next work | 영구적인 policy나 changelog |
| `specs/<feature>/` | 하나의 feature에 대한 spec, contract, plan, task, verification | Cross-feature policy나 장기 architecture |
| `src/` | Application source (Java/Spring Boot) | Documentation나 spec |
| `scripts/` | 반복 가능한 verification/smoke-test script | Ad hoc한 일회성 script |

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

`specs/001-push-event-delivery/`부터 `specs/006-field-registry/`까지는 이 project 고유의, GitHub Spec Kit 도입 이전의 feature tree입니다. 아직 Spec Kit 자체의 `specs/<NNN-feature>/` 구조로 migrate되지 않았습니다(실제 `specify init` 실행을 기다리는 중 — `docs/status/current-state.md`의 "Known constraints" 참고). `.specify/`의 나머지는 실제로 설치되면 pinned Spec Kit CLI에 속하며, 이 repository가 hand-fork하는 것이 아닙니다.

## Dependency direction

Adapter와 이 repository 자체의 README/spec은 authoritative document를 향해 안쪽으로 가리킬 수 있습니다. Authoritative document(`PROJECT.yaml`, `.specify/memory/constitution.md`, `.ai/constitution/documentation-policy.md`, `docs/product/`, `docs/architecture/`, accepted ADR)는 adapter의 문구나 external conversation history — 이 project가 bootstrap된 원래의 design document를 포함하여 — 에 의존하지 않습니다.

```text
README ───────────────┐
AGENTS.md ────────────┼──> PROJECT.yaml + .specify/memory/constitution.md + docs/product + docs/architecture
current-state ────────┘                 │
specs/<feature> ─────────────────────────┼──> accepted ADRs
```

## Directory policy

파일이 그 안에서 실제 책임을 가질 때만 디렉터리를 추가하세요. Phase 1이 필요로 하기 전에 정의되지 않은 module, preset, script를 위한 빈 구조를 만들지 마세요.

## Evolution rule

top-level area나 responsibility layer를 추가하기 전에, 기존 위치로 그것을 표현할 수 없는지 확인하세요. 장기적인 dependency direction, module boundary, 또는 modular-monolith-first decision에 대한 변경은 ADR을 필요로 합니다.
