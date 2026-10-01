> 이 문서는 [`porting-guide.md`](porting-guide.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# 포팅 가이드: `relayhub-<lang>` Sibling 만들기

`docs/decisions/ADR-0002-java-spring-boot-stack.md`는 이미 다른 언어로 만드는 같은 product의 구현을 별도 sibling repository(예: `relayhub-node`, `relayhub-go`)로 만들 것이지 이 repo를 재작성하지 않을 것이라고 결정했습니다 — 하지만 구체적으로 sibling 구현이 무엇을 그대로 복제해야 하고 무엇을 그 언어답게 다르게 해야 하는지는 한 번도 말한 적이 없습니다. 이 문서가 그 답입니다(자체 점검 발견 사항, 2026-10-02).

## 그대로 복제해야 하는 것 (product design — 언어 무관)

아래는 어떤 언어로 만들든 일치하지 않으면 *틀린* 것들입니다:

1. **Domain model과 그 관계.** `docs/architecture/domain-model.md` — Source -> SourceEvent -> SourceField, Target -> TargetEndpoint -> TargetField, SourceEvent <-Subscription-> TargetEndpoint, Event -> Delivery -> DeliveryAttempt. sibling 자신의 database schema가 이 repo의 실제 SQL과 똑같이 생길 필요는 없습니다(그 언어/ORM에 맞는 걸 쓰세요), 하지만 거기 나타내는 *entity와 관계*는 똑같아야 합니다.

2. **API contract.** `docs/architecture/api-contract.md`는 live로 generate되는 OpenAPI spec(*이* repository(Java)를 실행 중인 인스턴스에 대한 `GET /v3/api-docs`)을 가리킵니다 — 거기서 가져오세요, Java 소스에서 손으로 옮겨적지 마세요. 같은 path, 같은 request/response field 이름과 타입, 같은 status code. 이게 가장 기계적으로 검증하기 쉬운 포팅 대상이고, 그 spec을 손으로 유지보수하지 않고 generate하는 이유 자체가 그걸 믿고 대조할 수 있게 하기 위함입니다.

3. **Delivery semantics.** At-least-once delivery, "exactly once"는 절대 아님(constitution의 "external contract flexibility"와 "observable failure over hidden failure" 원칙 참고) — `docs/architecture/domain-model.md`의 "Delivery / DLQ / Replay state machine" 다이어그램이 재현해야 할 state machine입니다: `PENDING -> PROCESSING -> SUCCEEDED`, `PROCESSING -> RETRYING -> PROCESSING (loop) -> DEAD`, `DEAD -> REPLAYING -> PROCESSING`. 이 state machine의 *형태*가 이 repo의 구체적인 backoff 공식(선택적 jitter를 가진 exponential)보다 중요합니다 — 다만 그 언어 생태계에 명확하게 더 idiomatic한 대안이 없다면 그것도 그대로 가져오는 게 합리적인 기본 선택입니다.

4. **Idempotency 보장.** application-level 체크만이 아니라 DB-level uniqueness constraint가 뒷받침해야 합니다(`docs/decisions/ADR-0004-flyway-and-delivery-dedup.md`와 그 race가 실제임을 증명하는 `IdempotencyConcurrencyTest` 참고), 개념적으로 `(source_event_id, idempotency_key)` 범위로.

5. **Ingress URL 관례.** `docs/architecture/system-design.md`의 "Ingress URL convention" — method가 data-change semantics에 대응합니다(`CREATED=POST`, `REPLACED=PUT`, `PATCHED=PATCH`, `DELETED=DELETE`), path는 등록 데이터로부터 생성되며 integration별로 하드코딩되지 않습니다.

6. **Mapping 전략.** 타입을 보존하는 tree manipulation(이 repo: Jackson `JsonNode` + JSONPath), naive 문자열 `replace()`는 절대 안 됨 — `system-design.md`의 "Mapping strategy" 표 참고. 정확한 template 문법(`${$.jsonPath}`)은 이 repo의 구체적인 선택이라 반드시 신성한 건 아니지만, 그 *접근 방식*(template을 진짜 tree로 parse하고, 타입이 있는 값으로 치환하고, JSON을 문자열로 뭉개지 않는 것)은 스타일 취향이 아니라 하드 제약입니다 — string-replace 구현은 숫자/boolean/중첩 object를 조용히 망가뜨립니다.

7. **Constitution의 원칙들**, 전부 — `.specify/memory/constitution.md`: Source/Target에 RelayHub payload envelope를 강제하지 않음, feature 개수보다 reliability, 숨겨진 실패보다 관찰 가능한 실패(retry/DLQ/replay/audit, 절대 조용히 버리지 않음), 수동 교정보다 replay 가능한 구조, distribution보다 먼저 modular monolith(premature microservice 분리 없음), test에서 mock만 있는 코드보다 real integration.

8. **원래 설계 brief의 "이건 하지 마라" 하드 목록** (domain model이 여러 번 진화한 지금 놓치기 쉬워서 여기 보존합니다): RelayHub envelope 강제 금지; Source/Target이 integration을 위해 자기 contract를 바꾸도록 요구 금지; naive string-template replace 금지; "exactly once" delivery 주장 금지; database를 drop하고 reseed하는 대신 데이터를 forward로 migrate; distribution이 입증되기 전 microservice 분리 금지; 구현 안 된 기능을 UI에서 동작하는 것처럼 보여주지 않기(이 repo 자신의 `HMAC`/`OAUTH2`/`BEARER_TOKEN`/`BASIC` auth type 참고 — 선언만 되고, 아직 아무것도 안 하기 때문에 콘솔에서 선택지로 의도적으로 제공 안 함); 어떤 API 응답으로도 secret을 평문으로 노출하지 않기.

## Java/Spring 특유인 것 (transliterate하지 말고 그 언어답게 포팅하세요)

- **Spring Security의 `SecurityConfig`** — HTTP Basic + stateless + 단일 in-memory admin 계정은 이 repo의 구체적인(의도적으로 최소한인, 자체 Javadoc 참고) 선택입니다. sibling은 그 framework에 idiomatic한 auth middleware를 쓰되, 적용하는 *정책*만 일치하면 됩니다: `/api/**` write는 admin 인증 필요, 나머지는 public(`security/SecurityConfig.java` 자체 comment의 실제 기본 posture — "특별히 막지 않는 한 공개" — 를 다른 곳에 그대로 복제하기 전에 참고).
- **JPA/Hibernate entity mapping, Flyway migration.** idiomatic한 ORM/migration 도구를 쓰세요(예: Node라면 Prisma나 query builder + node-pg-migrate; Go라면 `sqlc`/GORM + `golang-migrate`). *additive-only, drop-and-reseed 금지* migration 규율(ADR-0004)이 반드시 가져와야 할 부분이지, Flyway 자체가 아닙니다.
- **Spring Kafka.** idiomatic한 Kafka client를 쓰세요. Transactional Outbox 패턴 자체(`outbox` package — Event와 Outbox row를 같은 DB transaction에 쓰고, 별도 publisher가 Outbox row를 Kafka로 옮김)가 유지해야 할 부분이지, `spring-kafka`의 구체적인 API가 아닙니다.
- **Testcontainers (Java).** 대부분의 주류 언어엔 동등한 게 있습니다(Testcontainers 자체도 Node/Go/Python binding이 있음) — 유지해야 할 건 그 *규율*입니다: CI에서 in-memory 대체재가 아니라 real Postgres/Kafka를 쓰는 것, 이 repo가 Postgres 고유 동작에서만 드러난 실제 버그를 겪었기 때문입니다(H2 등가물에선 절대 안 드러났던 것 — `docs/status/current-state.md`의 Stage 1 production migration incident 참고).
- **springdoc-openapi.** sibling 자신의 real controller/type으로부터 runtime에 OpenAPI spec을 generate해주는 걸 쓰세요 — 핵심(`api-contract.md` 참고)은 contract가 generate되고 항상 동기화되어 있다는 것이지, 구체적으로 springdoc이어야 한다는 게 아닙니다.
- **Package-by-feature Java 레이아웃** (`source/`, `sourceevent/`, `target/`, ... — `system-design.md`의 "Recommended domain modules", 2026-10-02에 실제 레이아웃에 맞춰 수정됨). *원칙*(기술 layer가 아니라 domain concept 단위로 조직화)은 유지할 가치가 있지만, Java의 디렉터리-per-package 관례 자체가 Go의 package 관례나 Node 프로젝트의 폴더 구조에 1:1로 매핑될 필요는 없습니다.
- **Gradle Kotlin DSL, multi-stage `Dockerfile`, `ci.yml`의 정확한 job 구조.** 새 언어에 idiomatic한 build 도구와 CI 단계를 쓰세요 — 하지만 이 repo 자신이 2026-10-02 자체 점검에서 뒤늦게 채워 넣어야 했던 *형태*는 유지하세요: frontend(있다면)와 backend 둘 다 CI에서 검증되는 것(둘 중 하나만이 아니라 — 그 점검의 "frontend had zero automated verification" 발견 사항 참고), 그리고 그 언어에 type-check가 있다면 Docker build가 단순 transpile/bundle 단계가 아니라 진짜 type-check를 포함해야 한다는 것.

## 실무 bootstrap 체크리스트

1. `docs/product/`(problem, goals, scope)를 읽으세요 — 이 repo의 구현이 아니라 product design 자체입니다.
2. 현재 entity model과 data flow를 위해 `docs/architecture/domain-model.md`와 `docs/architecture/system-design.md`를 읽으세요(`system-design.md`의 수정 note들을 주의 깊게 보세요 — 원래 MVP 시절 내용 중 어디가 drift됐고 어디가 아직 정확한지 그 note들이 말해줍니다).
3. 구현할 실제, 현재 API contract를 위해 이 repository를 실행 중인 인스턴스(또는 live production, `https://relayhub-java.developer.cleanbrain.me/v3/api-docs`)에서 `GET /v3/api-docs`를 가져오세요.
4. 위의 지속적인 원칙들을 위해 `.specify/memory/constitution.md`를 읽으세요.
5. `ADR-0002`의 naming convention을 따르세요: repository `relayhub-<lang>`, hostname `relayhub-<lang>.developer.cleanbrain.me`, 실제 Kubernetes namespace/manifest는 `cleanbrain-me-infra`와 조율하세요(이 repo가 아니라 그 repository가 배포를 소유합니다 — `docs/architecture/overview.md`의 "Deployment boundary" 참고).
6. 이 repository의 test suite를 이 구현만의 regression test가 아니라 기대 동작에 대한 실행 가능한 명세로 취급하세요 — 특히 `IdempotencyConcurrencyTest`, `DeliveryRetrySchedulerTest`, `DlqReplayIdempotencyTest`, `ApiKeyAuthenticationTest`, `MappingFieldRegistryValidationTest`는 각각 특정 acceptance 시나리오를 인코딩하고 있어서, 읽기만 하지 말고 새 구현에 대해서도 재현해볼 가치가 있습니다.
7. `docs/status/current-state.md`의 "Known constraints"/"Open decisions"와 `domain-model.md`의 "Known gaps"를 검토하세요 — 새 구현이 미뤄진 결정(Filter 미평가, rate limiting 없음 등) 각각을 그대로 물려받을지 아니면 다른 결정을 내릴지 의도적으로 정하세요; 둘 다 괜찮지만, 우연이 아니라 결정이어야 합니다.
