> 이 문서는 [`agent-behavior.md`](agent-behavior.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Agent Behavior

이 문서는 이 repository에서 작업하는 coding agent를 위한 공유 behavior contract를 정의합니다. Agent Development Starter로부터 project-specific 변경 없이 채택되었습니다.

## Before work

- 관련 agent adapter가 정의한 순서로 persistent context를 load합니다.
- 현재 request, current state, 관련 파일을 검토합니다.
- 기존 documentation과 implementation에서 evidence를 찾은 뒤, 영향을 받는 scope를 결정합니다.
- change 작업의 경우, implementation 전에 proportionate plan과 verification strategy를 준비합니다.

## During work

- user request와 accepted decision 범위 내에서 작업합니다.
- 기존 변경사항을 user-owned로 취급하고 관련 없는 작업을 덮어쓰지 않습니다.
- 중요한 assumption과 architectural change를 드러냅니다.
- 새 rule은 올바른 source of truth에 한 번만 기록합니다.

## Before completion

- 관련 verification을 실행하거나, 실행할 수 없었던 이유를 명시합니다.
- 최종 변경사항을 requirement, architecture, documentation 책임에 비추어 review합니다.
- 완료된 작업, 남은 risk, open decision을 구분합니다.
- 다음 session이 state 변경을 알아야 한다면 `docs/status/current-state.md`를 업데이트합니다.

completion을 주장하는 것과 verification을 실제로 보여주는 것은 다릅니다. verification evidence 없이 성공을 주장하지 마세요. RelayHub의 경우 구체적으로 다음을 포함합니다: `./gradlew test` 통과, Docker Compose가 깨끗하게 시작됨, integration/contract test 통과, reproducible demo scenario — `docs/product/goals.md`와 `specs/001-push-event-delivery/verification.md`를 참고하세요.
