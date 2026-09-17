> 이 문서는 [`ADR-0001-repository-first-context.md`](ADR-0001-repository-first-context.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# ADR-0001: Repository-First Context

- Status: Accepted
- Date: 2026-09-10
- Deciders: Project maintainer

## Context

AI coding workflow가 큰 initialization prompt와 이전 conversation에 의존할 때, session이나 agent가 바뀌면 project intent, design rationale, working state가 손실됩니다. 모든 context를 agent-specific instruction file에 중복하면 drift가 발생하고 핵심 지식이 특정 tool에 묶이게 됩니다.

RelayHub 고유의 design context는 원래 external conversation과 export된 문서 집합에 담겨 있었습니다. 그 origin이 미래의 session에서도 계속 사용 가능하다고 가정할 수 없으며, project는 outcome이 공유된 engineering principle과 architecture를 계속 따르면서도 간결한 task prompt를 허용해야 합니다. 이러한 이유로 이 project는 [`agent-dev-starter`](https://github.com/cleanbrain-developer/agent-dev-starter)의 repository-first-context foundation을 채택합니다.

## Decision

Repository를 persistent project context의 authoritative source로 사용합니다.

- Project identity를 `PROJECT.yaml`에 구조화합니다.
- Durable principle을 `.ai/constitution/`에 저장합니다.
- Product 및 architecture 설명을 `docs/` 아래에 저장합니다.
- 중요한 decision과 rationale을 ADR에 저장합니다.
- Working state를 `docs/status/current-state.md`에 저장합니다.
- Feature-level specification, contract, plan, task, verification을 `specs/<feature>/`에 저장합니다.
- `CLAUDE.md`는 공유 core를 load하는 thin agent-specific adapter로 유지합니다.
- external design conversation/문서의 decision이 repository에 persist되면 그것들을 장기적인 dependency에서 제거합니다(이 ADR과, 이 ADR이 동반하는 `docs/product/`, `docs/architecture/` 문서가 바로 그 persistence 단계입니다).

## Consequences

### Positive

- 새로운 session은 원래의 design conversation 없이도 작업을 이어갈 수 있습니다.
- 공유 policy가 하나의 source를 가지므로 adapter drift가 줄어듭니다.
- Decision, working state, task prompt가 서로 별도의 lifetime과 responsibility를 가집니다.

### Costs and risks

- Code와 documentation을 함께 유지해야 합니다.
- bootstrap 순서를 무시하는 agent는 중요한 context를 놓칠 수 있습니다.
- Markdown 규칙만으로는 compliance를 보장할 수 없습니다; implementation이 진행됨에 따라 deterministic gate(test, ArchUnit, CI)가 필요합니다.

## Alternatives considered

### Large reusable initialization prompt

시작하기는 쉽지만 지나치게 큰 prompt와 conversation 및 tool에 대한 dependency를 만듭니다. Rejected.

### Agent-specific documents as independent sources

Tool-specific optimization은 쉽게 만들지만 policy 중복과 drift를 발생시킵니다. Rejected.
