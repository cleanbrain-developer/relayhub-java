> 이 문서는 [`overview.md`](overview.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Architecture Overview

## Architectural style

Modular Monolith입니다. RelayHub는 microservice의 집합이 아니라, 명확한 internal module boundary를 가진(`system-design.md` 참고) 단일 배포 가능한 Spring Boot application으로 시작합니다. distributed complexity는 vertical slice가 그것이 필요함을 입증한 이후에만 추가됩니다(`docs/decisions/ADR-0001-repository-first-context.md`의 sibling ADR들과 `docs/status/current-state.md` 참고).

## Repository context layers (from Agent Development Starter)

```text
Task prompt
    v
Agent adapter (CLAUDE.md)
    v
PROJECT.yaml + Constitution + Product + Architecture + ADR + Current State
    v
Relevant repository evidence (including specs/)
    v
Plan -> Change -> Verify -> Review -> Persist state
```

- **Identity** — `PROJECT.yaml`은 name, type, lifecycle, supported agents, principles, current phase, canonical context path를 노출합니다.
- **Policy** — `.ai/constitution/`은 개별 task보다 오래 지속되는 engineering 및 documentation principle을 담습니다.
- **Knowledge** — `docs/product/`, `docs/architecture/`, `docs/decisions/`는 무엇을 만드는지, 왜 만드는지, 어떻게 구조화되어 있는지, 왜 중요한 선택을 했는지 설명합니다.
- **Feature specification** — `specs/<feature>/`는 하나의 feature slice에 대한 구체적인 spec, contract, plan, task, verification을 담습니다(`repository-structure.md` 참고).
- **Working state** — `docs/status/current-state.md`는 현재 phase의 완료된 작업과 next action을 설명합니다.
- **Adapter** — `CLAUDE.md`는 Claude Code가 공유 context를 discover하도록 도우며, tool-specific routing만 포함하고 policy를 중복하지 않습니다.

## Runtime architecture

RelayHub 고유의 system boundary, domain module, data flow, 목표로 하는 Transactional Outbox / Kafka delivery pipeline은 `system-design.md`를 참고하세요.

## Deployment boundary

이 repository는 RelayHub의 application source, build(Gradle), local Docker Compose environment를 소유합니다. `cleanbrain.me`를 위한 Kubernetes manifest는 소유하지 않습니다 — 그것들은 `cleanbrain-me-infra`에 속합니다(계획된 hostname: `relayhub-java.developer.cleanbrain.me`, namespace `cleanbrain-me-relayhub-java`, 이 service가 그곳에 배포될 경우/될 때). 이 repository에 Kubernetes manifest를 추가하지 마세요.

## Enforcement boundary

Markdown은 judgment와 절차를 안내하지만 compliance를 강제하지 않습니다. 절대 위반되어서는 안 되는 규칙은 궁극적으로 test, linter, architecture check(예: module boundary를 위한 ArchUnit), CI와 같은 deterministic quality gate로 승격되어야 합니다.
