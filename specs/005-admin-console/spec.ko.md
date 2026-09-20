> 이 문서는 [`spec.md`](spec.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Spec 005: Admin & Observability Console

**Feature Branch**: `005-admin-console`

**Created**: 2026-09-11

**Status**: Implemented

**Input**: 기록되지 않음 — 이 spec은 Spec Kit 도입 이전에 작성됨

## Summary

지금까지 만들어진 RelayHub의 모든 기능(Spec 001-004)은 REST API나 `curl`/script를 통해서만
접근 가능했고 — 화면이 없었습니다. 이 spec은 RelayHub 자체에 web UI를 제공합니다:
Source/Target/Subscription 관리와 Delivery/DLQ observability를, 별도의
`developer.cleanbrain.me` 사이트가 아니라 RelayHub 스스로가 제공합니다(그 사이트는 RelayHub의
API/Observability 표면을 *소비*하여 자신만의 view를 보여줄 것입니다) — 이 spec은 그것과는
독립적으로 RelayHub 자체의 first-class UI를 갖추는 것에 관한 것입니다.

공개된 read-only 절반(Observability)과 인증된 read-write 절반(Source/Target/Subscription 관리)
으로 나뉩니다 — RelayHub의 API는 현재 인증이 전혀 없으며, 인증되지 않은 공개 "create/delete
Source" form은 실제 보안 문제가 될 것입니다.

## In scope

- **React SPA**(`frontend/`, 별도의 build/toolchain, 같은 repo)를 static asset으로 build하여
  기존 Spring Boot app이 서빙합니다(`src/main/resources/static/`, API도 actuator도 ingress도
  아닌 모든 경로에 대해 `index.html`로 향하는 SPA fallback route) — 하나의 Docker image, 하나의
  Deployment, 새로운 Kubernetes Service/HTTPRoute는 필요 없습니다.
- **Spring Security**, HTTP Basic, 고정된 단일 admin 계정(`ADMIN_USERNAME`/`ADMIN_PASSWORD`
  환경 변수, user table/등록 절차 없음 — 개인 프로젝트 규모). 아래 나열된
  write/mutating endpoint만 보호합니다. 기존 endpoint의 현재 동작(Source-system이 사용하는
  것이지 admin이 사용하는 것이 아닌 `/ingress/v1/**` 포함)은 그 외에는 변경되지 않고 인증
  없이 유지되어야 합니다.
  - 보호 대상(인증 필요): `/api/sources`, `/api/targets`, `/api/subscriptions` 아래의 모든
    `POST`/`PUT`/`DELETE`(중첩된 `/api/sources/{sourceKey}/events` 포함), 그리고
    `POST /api/deliveries/{id}/replay`.
  - 공개(변경 없음): 모든 `GET`, 그리고 `/ingress/v1/**`와 `/actuator/**` 전체.
- 아직 존재하지 않는 list view를 위한 **새로운 read endpoint**: `GET /api/sources`,
  `GET /api/targets`, `GET /api/subscriptions`(각각 `INACTIVE`인 것까지 포함한 전체 set을
  반환 — UI는 삭제된 row를 숨기는 대신 status badge로 구분합니다 — 이 프로젝트의 개인 프로젝트
  규모 데이터 양에 맞춰 pagination은 아직 없음).
- 오늘날 존재하지 않는 edit/delete를 위한 **새로운 write endpoint**(Source/Target/Subscription은
  현재 create + get-by-key만 지원함):
  - `PUT /api/sources/{key}`, `PUT /api/targets/{key}`, `PUT /api/subscriptions/{id}` — mutable
    field(name, description, 그리고 type별 field: `Target.baseUrl`/`authenticationConfig`,
    `Subscription.targetMethod`/`targetPath`/`targetPayloadTemplate`/`retryPolicy`)를
    업데이트합니다. `key` 자체는 immutable하며(Ingress URL과 기존 Subscription에서
    load-bearing) — edit form에 포함되지 않습니다.
  - `DELETE /api/sources/{key}`, `DELETE /api/targets/{key}`, `DELETE /api/subscriptions/{id}` —
    **soft delete**: 이미 세 entity에 존재하는 `Status` enum과 기존 `findActive*` query
    pattern(`SubscriptionService.findActiveForSourceEvent`는 이미 이 값으로 filtering함)에
    맞춰 `status`를 `INACTIVE`로 바꿉니다. hard delete는 없습니다 — 이 row들은 손상되지 않고
    유지되어야 하는 Delivery/Event history에서 참조되며, Ingress/delivery 로직은 이미
    `INACTIVE`를 "적용되지 않음"으로 취급합니다.
  - `PUT /api/sources/{sourceKey}/events/{key}`, `DELETE /api/sources/{sourceKey}/events/{key}`
    — SourceEvent에 대해서도 동일하게 처리합니다. UI에서 Source가 자신의 event들과 함께
    편집/관리되므로 필요합니다.
- **Observability page**(공개): dashboard 요약(Delivery state별 count, 최근 ingress/outbox/
  delivery 활동 — 구현이 시작되면 무엇이 더 간단한지에 따라 `/actuator/prometheus` 데이터를
  재사용하거나 전용 요약 endpoint를 사용), state로 필터링 가능한 Delivery list(`DEAD`를 쉽게
  찾을 수 있도록), 각 attempt를 보여주는 Delivery detail.
- **Management page**(인증됨): Source/Target/Subscription list + detail(read 쪽은 공개
  list/get endpoint를 재사용); 위 endpoint들을 사용하는 각각의 create, edit, delete
  (deactivate) form/action; `DEAD` Delivery의 detail page에 있는 Replay button.

## Deliberately out of scope

- ~~Source/Target/Subscription/SourceEvent의 hard delete — Delivery/Event history가 이 row들을
  참조하므로 soft delete(status -> `INACTIVE`)만 지원합니다.~~ **대체됨(2026-09-12):** 이제 모든
  entity controller가 `DELETE .../{key}?hard=true`를 지원하며, console에는 "Delete permanently"
  버튼으로 노출된다. 참조하는 row가 아직 남아있으면(예: Source Event가 하나라도 등록된 Source)
  409로 막힌다 — 즉 무조건 불가능한 게 아니라, operator가 참조를 먼저 정리해야만 가능하다.
- Multi-user account, role, login/registration flow — 이 규모에서는 고정된 admin credential
  하나로 충분합니다.
- 별도의 `developer.cleanbrain.me` 사이트에 관한 어떤 것도 — 그것은 아직 시작되지 않은 별개의
  프로젝트로, 외부에서 이 console의 동일한 공개 API/Observability 표면을 호출하게 됩니다.
- ~~실시간 업데이트(WebSocket/SSE push) — page refresh 시의 polling이나 간단한 interval이면
  충분하며, live-streaming dashboard는 없습니다.~~ **대체됨(2026-09-12):** 이제 `GET
  /api/live/stream`(SSE)이 존재하며 console의 Live 페이지를 구동해 Source -> Target 트래픽을
  실시간으로 보여준다. in-memory emitter 목록만으로는 안 되어(레플리카마다 따로 놀기 때문에)
  Kafka topic(`LiveActivityBroadcaster`)으로 레플리카 전체에 fan-out한다. 이 console의 나머지는
  여전히 polling/reload 방식이다 — 전체를 실시간으로 재설계한 것이 아니라 이 하나의 live view만이다.
- "Delivery를 state로 filtering"을 넘어서는 Pagination, search, filtering — 이 규모의
  데이터 양에는 아직 필요하지 않습니다.

## Acceptance

- `GET /api/sources`, `/api/targets`, `/api/subscriptions`가 존재하며 현재 live 데이터를
  반환합니다(단순 test fixture가 아니라 이미 배포된 `demo-flightstatus` 시나리오를 대상으로
  검증됨).
- credential 없이 보호된 `POST`/`PUT`/`DELETE`를 호출하면 `401`을 반환하고, 올바른
  `ADMIN_USERNAME`/`ADMIN_PASSWORD`로는 성공합니다. Source/Target/Subscription/SourceEvent에
  대한 `DELETE`는 row를 제거하지 않고 database에 `status=INACTIVE` 상태로 남깁니다 — 코드로만
  추정하지 않고 직접 검증됨.
- `/ingress/v1/**`와 기존의 모든 `GET` endpoint의 동작은 변경되지 않았습니다 — 기존 test
  suite를 재실행하고 live 배포에 대한 수동 확인으로 검증됨.
- SPA는 local `bootRun`과 실제 `relayhub-java.developer.cleanbrain.me` 배포(단순 local
  build가 아니라 실제 재배포) 양쪽 모두에서 `/`로 접근 가능합니다 — 이 spec이 대체하는 현재의
  Whitelabel/404 root 응답을 대체합니다.
- 배포된 UI에서: operator가 현재 Source/Target/Subscription을 조회하고,
  relayhub-demo-systems가 이미 생성하고 있는 `demo-travelapp-vendor` DLQ entry를 보고, 그
  중 하나를 replay하고, Source/Target을 편집/비활성화할 수 있습니다 — `curl`이 아니라 browser로
  로그인한 상태에서.
