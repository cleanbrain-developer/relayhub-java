> 이 문서는 [`ADR-0008-unversioned-admin-api.md`](ADR-0008-unversioned-admin-api.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# ADR-0008: Admin API(`/api/**`)는 버전을 붙이지 않고 유지합니다 — 의도적이고 문서화된 공백

- Status: Accepted
- Date: 2026-10-06
- Deciders: Project maintainer

## Context

2026-10-06 scale-out 준비도 점검에서 `relayhub-<lang>` sibling repository 목표(`docs/architecture/porting-guide.md`)와 관련된 비일관성을 지적했습니다: Source 쪽 ingestion 계약은 명시적인 버전 세그먼트를 갖고 있는데(`/ingress/v1/{sourceKey}/{eventKey}`), admin 콘솔 자신의 API(`/api/sources`, `/api/targets`, `/api/deliveries` 등)는 버전 prefix가 전혀 없습니다. 만약 `relayhub-<lang>` sibling의 admin API 모양이 언젠가 이것과 달라져야 한다면 — 다른 필드, 다른 response envelope — 그게 *이* 계약에 대한 breaking change인지 받아들일 수 있는 구현 차이인지 판단할 기존 관례가 없습니다.

## Decision

**`/api/**`는 버전 없이 유지합니다.** `/ingress/v1/**`만 버전 세그먼트를 갖고, 그 결정은 그 endpoint에만 해당하는 특정한 이유로 유지됩니다: 그건 RelayHub가 양쪽의 동시 업그레이드를 조율할 수 없는, *외부의, 독립적으로 배포되는* 시스템(실제 Source)이 의존하는 유일한 계약입니다. `/api/**`는 오직 이 repository 자신의 frontend에서만 소비되며, 같은 repository에서, 같은 시점에, 같은 CI 파이프라인(`.github/workflows/ci.yml`의 `build-and-push` → `deploy` 순서 참고)으로 빌드되고 배포됩니다 — frontend와 backend가 production에서 서로 다른 버전으로 실행된 적이 없습니다. `/api/**` 버저닝이 풀어줄 실제 호환성 문제가 오늘은 없습니다.

이 ADR이 답하려는 `relayhub-<lang>` porting 질문에 대해서는: sibling 구현의 admin API가 이것과 byte-for-byte 일치해야 **하는 건 아닙니다**. `docs/architecture/api-contract.md`와 생성된 OpenAPI spec(`/v3/api-docs`)은 *이* 구현의 현재 모양을 참고 자료로 설명하는 것이지, 언어 간에 고정된 계약이 아닙니다 — `/ingress/v1/**` 관례와 그 아래의 domain model(`docs/architecture/domain-model.md`)만이 sibling이 그대로 복제해야 하는 실제 product design입니다, `ADR-0002`의 원래 "그대로 복제 vs 그 언어답게 포팅" 구분에 따라서요.

## Consequences

### Positive

- 실재하지 않는 호환성 문제를 위한 추측성 버저닝 scheme(`/api/v1/**`? header 기반? content-negotiation 기반?)이 없습니다 — frontend와 backend는 항상 함께 배포됩니다.
- 한 번도 실제로 올린 적 없는 버전 번호가 `/api/**`에 붙어있는 혼란스러운 비대칭을 피합니다(아무도 올릴 필요가 없었던 `v1`은, 버전 마커가 아예 없는 것보다 더 나쁩니다 — 실재하지 않는 안정성 계약을 암시하니까요).

### Costs and risks

- 만약 `/api/**`가 언젠가 이 repository 자신의 frontend가 아닌 다른 것 — CLI 도구, 서드파티 통합, 이 backend를 재사용하는 sibling 콘솔 — 에서 소비된다면, 그 통합이 움직이는 target을 안전하게 따라갈 수 있기 전에 이 결정을 재검토해야 합니다.
- `relayhub-<lang>` sibling의 admin API 모양이 이것과 달라지는 건 위 결정에 따르면 예상되고 괜찮지만, `api-contract.md`가 sibling 간에 동일하다고 가정할 수 없다는 뜻이기도 합니다 — 각 sibling 자신이 생성한 OpenAPI spec이 이 문서가 아니라 그 sibling의 실제 source of truth입니다.

## Alternatives considered

### 딱 하나의 버전만 영원히 존재하더라도 지금 `/api/v1/**`를 추가한다

기각 — 위 "Costs and risks" 참고: 사용되지 않는 버전 마커는 이 프로젝트가 실제로 지킬 필요가 없었던 안정성 보장을 암시하고, 현재의 어떤 이득도 없이 기존 모든 frontend 호출부를 수정해야 합니다.

### 모든 `relayhub-<lang>` sibling의 admin API가 이것과 정확히 일치해야 한다고 요구한다

기각 — 이건 내부 구현 디테일(이 특정 Java/Spring/React 스택이 자신의 CRUD endpoint를 어떻게 모양 짓는지)을 언어 간 계약으로 바꾸는 것인데, `ADR-0002`의 "그대로 복제 vs 그 언어답게 포팅" 구분은 이미 admin API가 그런 게 아니라고 말하고 있습니다: domain model과 ingress 계약만이 그대로 복제해야 할 실제 product design입니다.

## Revisit trigger

이 repository 자신의 frontend가 아닌 무언가가 `/api/**`를 소비하면서 배포를 거쳐도 그 모양이 안정적으로 유지되는 데 의존하게 되는 순간 — `ADR-0005`/`ADR-0006`/`ADR-0007`이 이미 쓰는 것과 동일한 "실제 필요가 생기면" 패턴입니다.
