> 이 문서는 [`documentation-policy.md`](documentation-policy.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Documentation Policy

Agent Development Starter로부터 project-specific 변경 없이 채택되었습니다.

## Authority

> Conversation은 임시적입니다. Repository가 authoritative합니다.

Conversation과 external link(이 project가 bootstrap된 원래의 design context 포함)는 discovery나 bootstrap input이 될 수 있지만, 장기적인 dependency는 아닙니다. durable agreement는 repository에 persist되기 전까지는 완료된 것이 아닙니다.

## Single responsibility

- Project identity와 structured phase: `PROJECT.yaml`
- Product purpose, goals, scope: `docs/product/`
- Structure와 context model: `docs/architecture/`
- 중요한 decision과 rationale: `docs/decisions/`
- 현재 progress, next work, open decision: `docs/status/current-state.md`
- Durable development principle: `.ai/constitution/`
- Tool-specific bootstrap 차이: agent adapter (`CLAUDE.md`)
- Feature-level specification, contract, plan, task, verification: `specs/<feature>/`

동일한 policy를 여러 파일에 중복해서 두지 마세요. summary가 유용한 경우, authoritative path로 link하세요.

## Decision records

architecture, technology stack, distribution strategy와 같이 장기적인 영향을 미치는 선택은 ADR로 기록하세요. ADR은 context, decision, consequences, status를 포함해야 합니다. accepted ADR은 다른 ADR이 대체하기 전까지 유효합니다.

## Status hygiene

`current-state.md`는 meeting log나 완전한 changelog가 아닙니다. 현재 phase를 재구성하는 데 필요한 완료된 작업, 진행 중인 작업, next action, open decision만 유지하세요.

## Maintenance

code나 structure 변경으로 documentation이 사실과 달라지면, 같은 변경 안에서 documentation을 업데이트하세요. 오래된 guidance는 제거하고, history를 보존해야 할 때는 ADR이나 version control을 사용하세요.
