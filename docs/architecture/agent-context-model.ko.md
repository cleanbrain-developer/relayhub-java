> 이 문서는 [`agent-context-model.md`](agent-context-model.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다. Agent bootstrap은 이 번역본이 아니라 영어 원본을 읽습니다.

# Agent Context Model

Agent Development Starter로부터 project-specific 변경 없이 채택되었습니다.

## Context classes

### Permanent context

project purpose, scope, architecture, engineering principles, accepted decision을 포함하여 session 간에 지속되는 정보입니다. `PROJECT.yaml`, `.ai/constitution/`, product 및 architecture 문서, ADR에 저장합니다.

### Working context

current phase, 최근 완료된 작업, next action, open decision을 포함하여 진행 상황에 따라 변하는 정보입니다. `docs/status/current-state.md`에 저장합니다.

### Feature context

단일 feature의 specification, contract, plan, task, verification입니다. `specs/<feature>/`에 저장합니다. permanent context에 의존하며, permanent context는 이것에 의존하지 않습니다.

### Task context

현재 user request, 관련 code, 임시 research finding입니다. 필요할 때만 load합니다. durable value를 가지게 되면 적절한 permanent, working, feature source에 persist합니다.

## Bootstrap order

1. `CLAUDE.md`에서 시작합니다.
2. `PROJECT.yaml`을 읽어 project와 phase를 파악합니다.
3. constitution(`.ai/constitution/`)을 읽어 behavioral boundary를 이해합니다.
4. product 문서(`docs/product/`)를 읽어 목적과 scope를 이해합니다.
5. architecture 문서(`docs/architecture/`)를 읽어 구조와 책임을 이해합니다.
6. 현재 작업과 관련된 accepted ADR(`docs/decisions/`)을 읽습니다.
7. feature-level 세부사항을 위해 관련 `specs/<feature>/` 디렉터리를 읽습니다.
8. `docs/status/current-state.md`를 읽어 현재 위치와 다음 작업을 파악합니다.
9. request와 관련된 repository evidence를 검토합니다.
10. Plan, change, verify, review를 수행합니다.
11. durable decision과 working-state 변경사항을 repository에 persist합니다.

## Conflict handling

- user request는 작업 목표를 정의하지만 accepted architecture를 조용히 폐기하지 않습니다.
- 구체적인 accepted ADR은 일반적인 architecture description보다 우선합니다.
- current state는 principle이나 design을 재정의하지 않습니다.
- `specs/<feature>/` 문서는 product나 architecture decision을 재정의하지 않고, 그것들을 구현합니다.
- 해결되지 않은 conflict는 assumption 뒤에 숨기지 말고 report합니다.

## Bootstrap acceptance test

clean session에서 external link 없이 `CLAUDE.md`만 제공합니다. agent가 `docs/product/goals.md`("Success criteria")의 다섯 가지 acceptance question에 대해, 모든 답변이 repository documentation으로 추적 가능하게 답할 수 있으면 context recovery가 성공한 것입니다.
