> 이 문서는 [`AGENTS.md`](AGENTS.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다. Agent bootstrap은 이 번역본이 아니라 영어 원본을 읽습니다.

# AGENTS.md

이 파일은 이 repository에서 작업하는 모든 coding agent를 위한 단일 entry point입니다. 공유 정책이나 project design의 source of truth가 아니며, 그것들로 안내하고 이 repository에서 모든 agent가 따르는 behavioral contract를 명시하는 역할만 합니다. 이 project는 현재 Claude Code만 지원하지만(`PROJECT.yaml`의 `supported_agents` 참고), 이 파일은 Claude 전용 adapter가 아니라 agent-agnostic한 `AGENTS.md` convention을 따릅니다 (`agent-dev-starter`의 `ADR-0011` 참고) — 이전에 별도로 존재했던 `CLAUDE.md` adapter는 이 파일로 merge되어 제거되었습니다.

## Context bootstrap

먼저 `PROJECT.yaml`을 읽은 다음, `docs/architecture/agent-context-model.md`에 정의된 bootstrap 순서와 conflict-handling 규칙을 따르세요. 이 파일은 그 순서를 다시 설명하지 않습니다 — 만약 이 section과 그 문서가 서로 다르면, `agent-context-model.md`가 우선합니다.

## Working contract

### Before work

- `agent-context-model.md`가 정의한 순서로 persistent context를 load합니다.
- 현재 request, `docs/status/current-state.md`, 관련 파일을 검토합니다.
- change를 제안하기 전에 기존 documentation과 implementation에서 evidence를 찾은 뒤, 영향을 받는 scope를 결정합니다.
- change 작업의 경우, implementation 전에 proportionate plan과 verification strategy를 준비합니다.

### During work

- user request와 accepted decision 범위 내에서 작업하며, 기존 변경사항을 user-owned로 취급하고 관련 없는 작업을 덮어쓰지 않습니다.
- 중요한 assumption과 architectural change를 조용히 결정하지 않고 드러냅니다.
- 새 rule은 올바른 source of truth에 한 번만 기록합니다 — 이 파일에는 절대 기록하지 않습니다.
- External contract flexibility는 RelayHub 전용 payload envelope 도입보다 우선하며, reliability는 feature 수보다 우선합니다. `.specify/memory/constitution.md`와 `docs/product/goals.md`를 참고하세요.

### Before completion

- 관련 verification을 실행하거나, 실행할 수 없었던 이유를 명시합니다. green automated check와 실제로 실행 중인 system에 대한 direct verification을 구분합니다.
- 최종 변경사항을 requirement, architecture, documentation 책임에 비추어 review합니다.
- 완료된 작업, 남은 risk, open decision을 구분합니다.
- 다음 session이 state 변경을 알아야 한다면 `docs/status/current-state.md`를 업데이트합니다.
- 이 변경이 `.ko.md` companion이 있는 문서를 수정했다면, 같은 변경 안에서 companion도 업데이트합니다(`agent-dev-starter`의 `ADR-0005`) — 오래된 번역은 follow-up task가 아니라 defect입니다.

completion을 주장하는 것과 verification을 실제로 보여주는 것은 다릅니다. verification evidence 없이 성공을 주장하지 마세요. RelayHub의 경우 구체적으로 다음을 포함합니다: `./gradlew test` 통과, Docker Compose가 깨끗하게 시작됨, integration/contract test 통과, reproducible demo scenario — `docs/product/goals.md`와 `specs/001-push-event-delivery/verification.md`를 참고하세요.

## Conflict handling

- user request는 작업 목표를 정의하지만 accepted architecture를 조용히 폐기하지 않습니다.
- 구체적인 accepted ADR은 일반적인 architecture description보다 우선합니다.
- `current-state.md`는 principle이나 design을 재정의하지 않습니다.
- `specs/<feature>/` 문서는 product나 architecture decision을 재정의하지 않고, 그것들을 구현합니다.
- 해결되지 않은 conflict는 assumption 뒤에 숨기지 말고 report합니다.

현재 phase와 다음 작업에 대한 source of truth로 `docs/status/current-state.md`를 사용하세요.
