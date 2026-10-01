> 이 문서는 [`ADR-0006-no-ingress-rate-limiting.md`](ADR-0006-no-ingress-rate-limiting.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# ADR-0006: Ingress Rate Limiting 없음 (아직) — 의도적이고 문서화된 공백

- Status: Accepted
- Date: 2026-10-02
- Deciders: Project maintainer

## Context

`authenticationType=NONE`인 Source(이 배포의 모든 demo Source, 그리고 새로 등록되는 Source의 기본값)는 `/ingress/v1/{sourceKey}/{eventKey}`로 들어오는 ingress 요청을 어떤 rate limiting도 없이 그대로 받습니다. 2026-10-02 템플릿 완성도 자체 점검에서 이걸 실제 공백으로 지적했습니다: 이 repository는 앞으로 나올 `relayhub-<lang>` sibling 서비스들이 찍어낼 패턴이 될 예정인데(`docs/decisions/ADR-0002-java-spring-boot-stack.md` 참고), rate limiting이 조용히 빠져있는 상태는 한 번도 의도적으로 선택된 적 없이 그대로 복제되기 쉬운 종류의 것입니다.

이 배포의 실제 트래픽(`relayhub-demo-systems`, 몇 초마다 소수의 요청)에서는 rate limiting 부재가 실무적으로 문제가 되지 않습니다. 그렇다고 그게 곧 근거 있는 결정이라는 뜻은 아니며, 이 ADR이 대신 기록하는 게 바로 그 결정입니다.

## Decision

**지금은 rate limiting을 추가하지 않습니다.** 이건 실수로 빠뜨린 게 아니라 명시적이고 근거 있는 보류입니다 — 미래의 독자(sibling-language 구현 포함)가 조용한 공백이 아니라 결정을 보도록 여기 기록해둡니다.

실제로 존재하는 두 가지 완화책이 전혀 없는 건 아닙니다:

- Source를 `authenticationType=API_KEY`로 전환할 수 있습니다(`common/ApiKeyAuth.java`/`IngressService.authenticate` 참고) — rate limiting은 아니지만, 최소한 어떤 ingress 요청이든 받아들여지기 전에 credential을 요구하게 되어, operator가 보호하고 싶은 Source에 대해 "인터넷 아무나 임의의 event를 post할 수 있는" 노출을 막아줍니다.
- `GlobalExceptionHandler`/JSON Schema validation(`IngressService.validateSchema`)이 저장되기 전에 형식이 잘못된 payload를 거부해서, rate limiter가 없어도 잘못된 요청 하나의 비용을 제한합니다.

## Consequences

### Positive

- 지금 이 배포엔 없는 위험을 위해 추가 복잡도(rate limiter는 범위에 대한 결정이 필요합니다 — per-IP? per-Source? per-API-key? token bucket 파라미터는? 여러 정상 호출자가 공유하는 NAT exit IP에서는 어떻게 할지?)를 들이지 않습니다.
- 이 공백이 이제 눈에 안 보이는 것이 아니라 문서화된 의도적 결정이 됐습니다 — sibling 구현은 이 결정을 조용히 그대로 물려받는 대신 스스로 정보에 기반한 선택을 할 수 있습니다.

### Costs and risks

- `authenticationType=NONE`로 남아있는 Source(기본값)는 요청 flood에 대해 아무 보호도 없습니다 — 악의적인 행위자뿐 아니라, 우발적인 경우(예: 잘못 설정된 실제 Source 시스템이 tight loop로 재시도하는 경우)로부터도요. 이 프로젝트의 demo 트래픽 규모에서는 버틸 수 있지만, 실제(데모가 아닌) Source가 연결된 real production 규모에서는 그렇지 않을 겁니다.
- 이 결정은 **실제(데모가 아닌) Source가 연결되는 순간 다시 읽기만이 아니라 재검토해야 합니다** — `domain-model.md`의 "Known gaps"가 Delivery Attempt 접근 정책과 선언만 되고 동작하지 않는 authentication type들을 재검토할 때 쓰는 것과 동일한 트리거입니다.

## Alternatives considered

### 지금 기본적인 per-IP rate limiter를 추가한다 (예: Bucket4j, 또는 직접 만든 token bucket)

이번 단계에서는 기각 — 실제 rate-limiting *정책*(per-IP vs per-Source vs per-API-key, 어떤 한도, 정상적인 burst엔 어떻게 대응할지)은 이 ADR의 context가 잘 결정할 만큼 충분한 정보를 갖고 있지 않은 product 결정이고, 지금 추측으로 정하면 실제 Source의 정상 burst 패턴엔 너무 엄격하거나, 의미가 없을 만큼 너무 느슨할 위험이 있습니다. 실제 Source의 실제 트래픽 형태가 파악됐을 때 재검토하되, 추측으로 미리 하지 않습니다.

### 모든 Source에 `authenticationType=API_KEY`를 강제한다 (`NONE` 옵션 제거)

기각 — 이건 registration model 자체의 유연성을 바꾸는 것이고(간단한 로컬 테스트나 자체 network-level access control이 있는 Source가, RelayHub 스스로 보호해야 할 API key를 굳이 관리하도록 강제될 필요는 없습니다), 유효한 key를 *가진* Source로부터의 요청 flood를 실제로 rate-limit하지도 못합니다. rate-limiting 질문과는 직교하는 문제이지, 대체재가 아닙니다.
