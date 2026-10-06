> 이 문서는 [`ADR-0007-single-tenant-single-environment.md`](ADR-0007-single-tenant-single-environment.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# ADR-0007: Single-Tenant, Single-Environment 콘솔 — 의도적이고 문서화된 공백

- Status: Accepted
- Date: 2026-10-06
- Deciders: Project maintainer

## Context

2026-10-06 scale-out 준비도 점검(`relayhub-<lang>` sibling 서비스를 이 템플릿에서 찍어내기 전 — `docs/architecture/porting-guide.md` 참고)에서 이 코드베이스 어디에도 tenant 개념이 없다는 걸 확인했습니다: 어떤 entity/controller/frontend type에도 `tenantId`/`organizationId`/`accountId` 필드가 없고, 고정된 admin 계정이 정확히 하나뿐입니다(`relayhub.admin.username`/`relayhub.admin.password`, `SecurityConfig.java` 참고). 콘솔 자체에도 environment 개념이 없습니다 — Spring profile(`dev`/`test`/`demo`/production)이 *설정*을 선택하긴 하지만, 하나의 실행 중인 인스턴스 안에서 "staging"과 "production" 데이터를 operator가 보거나 전환할 수 있는 UI나 API는 없습니다.

scale-out 하기 전에, 이건 검토 없는 기본값이 아니라 결정이어야 합니다: "RelayHub"가 의미하는 게 *이를 통합하는 팀/서비스마다 하나의 인스턴스*(오늘의 실제 모습 — Hetzner 배포 하나, admin 계정 하나, Postgres 데이터베이스 하나, 하나의 demo 통합 세트를 서비스)인지, 아니면 *여러 팀이 자기 Source/Target/Subscription을 등록하는 하나의 공유 인스턴스*인지. 두 그림은 아주 다른 domain model을 요구합니다 — 후자는 거의 모든 entity에 `tenantId`가 필요하고, tenant별 admin 계정이 필요하고, query 레이어에서 강제되는 데이터 격리가 필요한데, 지금은 이 중 아무것도 없습니다.

## Decision

**RelayHub는 배포된 인스턴스마다 single-tenant, single-environment로 유지합니다.** 더 많은 통합이나 더 많은 서비스로 scale-out한다는 것은 하나의 공유 인스턴스에 multi-tenancy를 추가하는 게 아니라, RelayHub 인스턴스를 더 배포하는 것(팀/서비스마다 하나, `ADR-0002`가 이미 세운 `relayhub-<lang>` sibling repository 모델과 일치)을 의미합니다.

이게 이 프로젝트의 실제 규모(`cleanbrain-me-infra`의 2 vCPU/4 GB/40 GB Hetzner box, SaaS 플랫폼이 아니라 개인 프로젝트 배포 대상)와 이미 만들어진 것에 자연스럽게 맞습니다: admin 콘솔, auth 모델, domain model 모두 이미 "operator 한 명, 통합 세트 하나"를 전제로 하고 있습니다 — 아직 존재하지 않는 필요를 위해 여기에 tenant 격리를 소급 적용하면 거의 모든 entity와 controller를 건드려야 합니다.

## Consequences

### Positive

- 아직 실재하지 않는 요구사항을 위한 추측성 schema/auth 재작업이 없습니다 — 이 프로젝트가 이미 갖고 있는, 실제 필요보다 앞서 만들지 않는 규율과 일치합니다(ADR-0005, ADR-0006 참고).
- `relayhub-<lang>` porting guide의 domain model이 계속 간단하게 복제 가능한 상태로 남습니다: sibling 구현이 tenant scoping을 전혀 고민할 필요가 없습니다.
- 명확한 운영 모델: 배포당 Kubernetes namespace 하나, 데이터베이스 하나, admin 계정 하나 — `cleanbrain-me-infra`의 다른 서비스들이 이미 배포되는 방식과 일치합니다.

### Costs and risks

- RelayHub가 언젠가 하나의 인스턴스로 여러 독립된 팀/서비스를 서비스해야 한다면, 이건 점진적으로 덧붙일 수 있는 게 아니라 나중에 실제로 침습적인 마이그레이션(모든 entity, 모든 query, 모든 API response를 tenant-scoping)이 됩니다.
- 오늘 여러 실제 operator가 하나의 인스턴스를 공유한다면 단일 admin credential을 공유해야 합니다 — operator별 audit trail이나 접근 범위 구분이 없습니다. 오늘의 단일-operator 규모에서는 괜찮지만, 그게 바뀌면 재검토해야 합니다.

## Alternatives considered

### 나중의 마이그레이션을 피하기 위해 지금 쓰지 않는 `tenantId` 컬럼을 추가한다

기각 — 어디에도 강제되지 않는 사용하지 않는 컬럼은 컬럼이 없는 것보다 더 나쁩니다: 실제로는 없는 tenant 격리가 존재하는 것처럼 보이게 하는데, 이건 정직한 부재보다 더 위험합니다. multi-tenancy가 언젠가 필요해진다면, 지금 추측으로 만드는 대신 실제 요구사항(tenant는 누가 어떻게 만드는지? Source/Target registry는 공유되는지 격리되는지, delivery history도인지?)에 맞춰 설계해야 합니다.

### 다음 `relayhub-<lang>` sibling부터는 처음부터 multi-tenant로 만든다

기각 — `ADR-0002`가 sibling repository를 가상의 미래 domain model이 아니라 이 프로젝트의 *현재* domain model을 복제하도록 묶어둔 것과 같은 이유입니다 — sibling은 어딘가에 실제로 shipped된 적 없는 추측성 재설계가 아니라, 여기서 실제로 증명된 것을 포팅해야 합니다.

## Revisit trigger

두 번째 실제(데모가 아닌) 팀이나 서비스가 자기만의 배포를 받는 대신 진짜로 하나의 RelayHub 인스턴스를 공유해야 하는 순간 — `ADR-0005`/`ADR-0006`이 이미 자신들의 보류에 쓰는 것과 동일한 "실제 필요가 생기면" 트리거입니다.
