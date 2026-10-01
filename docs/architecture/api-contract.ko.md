> 이 문서는 [`api-contract.md`](api-contract.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# API Contract (OpenAPI)

REST API contract는 실제 `@RestController`/record DTO 코드로부터 runtime에 생성됩니다(springdoc-openapi) — 손으로 유지보수하지 않습니다. 자체 점검 발견 사항(2026-10-02): `system-design.md`의 기술 스택 표는 항상 `Contract: OpenAPI / JSON Schema`라고 적혀 있었지만, 실제로는 OpenAPI generator가 지금까지 연결된 적이 없어서 기계적으로 검증 가능한 API contract가 아예 없었습니다. 이건 명시적으로 계획된 multi-language sibling(`relayhub-node`, `relayhub-go`, ... — `docs/decisions/ADR-0002-java-spring-boot-stack.md` 참고)에 직접적으로 중요합니다: sibling 구현은 이 repo의 Java 소스를 손으로 읽는 게 아니라, 항상 실제 코드와 동기화된 이 generated contract를 기준으로 검증해야 합니다.

## 어디서 구하나

- **브라우저 UI**: `/swagger-ui.html` (로컬: `http://localhost:8080/swagger-ui.html`; production: `https://relayhub-java.developer.cleanbrain.me/swagger-ui.html` — 이 배포의 다른 모든 GET endpoint와 마찬가지로 public입니다, `security/SecurityConfig.java` 참고).
- **Raw spec** (codegen이나 diff 용도): `GET /v3/api-docs`.

**이 repository에 static 파일로 커밋해두지 않습니다** — 커밋해둔 snapshot은 controller나 DTO가 바뀌는 순간 바로 stale해지기 시작하는데, 이건 정확히 이 문서가 막으려는 종류의 staleness입니다(실제로 그런 일이 벌어졌던 사례는 `system-design.md`의 이제는 수정된 기술 스택 표 참고). 필요할 때 실행 중인 인스턴스(로컬이든 production이든)에서 직접 가져와 쓰세요.

## 무엇을 커버하나

`/api/**` 아래 모든 `@RestController`가, 실제 request/response record DTO로부터 생성됩니다 — field 이름, 타입, enum 값 전부 실행 중인 코드가 실제로 받아들이고 반환하는 것과 정확히 일치합니다(2026-10-02 검증: 31개 path, `SubscriptionResponse`의 실제 shape — 당시 가장 최근에 추가된 필드였던 `mappingWarnings` 포함 — 와 직접 대조 확인).

## 알려진 한계: `/ingress/v1/**`

Ingress endpoint(`IngressController`)는 등록된 모든 Source Event를 동적으로 처리하는 단일 Spring `@RequestMapping(value = "/ingress/v1/**")`입니다 — 특정 Source Event의 실제 path는 compile time에 고정되지 않고 등록 데이터(`Source.key` + `SourceEvent.key`)로부터 생성됩니다. OpenAPI는 "path가 현재 등록된 것에 따라 달라진다"를 표현할 방법이 없어서, generated spec엔 쓸모없는 literal `/ingress/v1/**` 항목으로만 나타납니다. 실제 관례(method mapping, path 형태, payload 규칙)는 `system-design.md`의 "Ingress URL convention" 섹션과 `domain-model.md`의 "Event delivery flow" 다이어그램을 참고하세요 — 그리고 특정 배포 환경에서 실제로 등록된 ingress path를 확인하려면 `GET /api/sources/{sourceKey}/events`를 호출하면 각 `SourceEvent.ingressPath`/`ingressMethod`를 확인할 수 있습니다.

## Authentication

`/api/**`의 write(`POST`/`PUT`/`DELETE`)는 admin role의 HTTP Basic이 필요합니다; 나머지(`GET` 전체, `/ingress/v1/**`, `/actuator/**`)는 public입니다 — `security/SecurityConfig.java` 참고. Generated spec은 이걸 전역 적용되는 하나의 `basicAuth` security scheme으로 선언합니다; "GET은 public, write는 admin-only"를 operation 단위로 세밀하게 구분하진 않습니다(generated spec의 cosmetic한 한계일 뿐, 실제 enforcement는 정확하고 테스트되어 있습니다 — `AdminConsoleApiTest` 참고).
