> 이 문서는 [`CLAUDE.md`](CLAUDE.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다. Agent bootstrap은 이 번역본이 아니라 영어 원본을 읽습니다.

# Claude Code Project Adapter

이 파일은 Claude Code를 위한 repository entry adapter입니다. 공유 정책이나 project design의 source of truth가 아니며, Claude Code를 아래의 repository context로 안내하는 역할만 합니다.

## Required context

먼저 `PROJECT.yaml`을 읽은 다음, `docs/architecture/agent-context-model.md`에 정의된 bootstrap 순서와 conflict-handling 규칙을 따르세요. 이 adapter는 그 순서를 다시 설명하지 않습니다 — 만약 이 목록과 해당 문서가 서로 다르면, `agent-context-model.md`가 우선합니다.

## Working contract

- 기존 evidence를 검토하고 change plan을 준비합니다.
- 가능한 가장 작은 coherent change를 구현하고, 검증한 뒤, 최종 diff를 review합니다.
- architectural decision이나 durable state를 conversation history에만 남기지 않습니다.
- 공유 규칙은 이 adapter가 아니라 적절한 constitution이나 documentation source에 추가합니다.
- 해결되지 않은 conflict와 open decision은 assumption으로 조용히 처리하지 말고 명시적으로 드러냅니다.
- External contract flexibility는 RelayHub 전용 payload envelope 도입보다 우선하며, reliability는 feature 수보다 우선합니다. `.ai/constitution/engineering-principles.md`와 `docs/product/goals.md`를 참고하세요.

현재 phase와 다음 작업에 대한 source of truth로 `docs/status/current-state.md`를 사용하세요.
