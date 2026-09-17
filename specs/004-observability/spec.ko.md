> 이 문서는 [`spec.md`](spec.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Spec 004: Observability / Portfolio Quality

## Summary

원래 design의 roadmap(`docs/product/goals.md`, "Long-term direction", Phase 3)에서 마지막 단계로,
RelayHub의 runtime 동작을 관측 가능하게 만드는 것 — metrics, tracing, dashboard — 그리고
failure -> DLQ -> replay 스토리를 재현 가능한 demo로 증명하고, 기본적인 load/reliability check를
추가하는 것이 목표입니다. Spec 001-003에서 만든 모든 것(retry, DLQ, replay, async delivery)은
실제로 동작하는 모습을 *볼 수* 있어야만 진짜로 신뢰할 수 있습니다. 이 spec은 새로운 delivery
동작이 아니라 가시성(visibility)에 관한 것입니다.

## In scope

- **Metrics**: Spring Boot Actuator + Micrometer를 사용하며, `/actuator/prometheus`에 노출됩니다.
  JVM/HTTP 기본 지표 외에 domain 전용 counter/timer를 추가합니다: dedup 결과별 ingress event 수신,
  outbox publish 시도, terminal state(`SUCCEEDED`/`DEAD`)별 delivery, delivery attempt latency,
  replay 호출.
- **Dashboards**: `docker-compose.yml`에 Prometheus(app을 scrape)와 Grafana(위 counter들을
  다루는 Prometheus datasource와 starter dashboard 하나가 provisioning됨)를 local-dev 전용
  서비스로 추가합니다.
- **Tracing**: Micrometer Tracing을 OpenTelemetry에 연결(bridge)하여 local Zipkin instance
  (`docker-compose.yml`에 추가됨)로 export합니다. 이를 통해 하나의 ingress request의 trace —
  ingress -> outbox write -> (async gap) -> Kafka consume -> delivery attempt(s) -> Target call —
  를 end to end로 검사할 수 있습니다.
- **Failure -> DLQ -> Replay demo**: `scripts/dlq-replay-demo.sh`는, 이 세션에서 Spec 001-003에
  대해 수작업으로 반복해온 수동 검증을 스크립트로 재현 가능하게 만든 것입니다 — Source/Target
  A(성공)/Target B(실패)/Subscription을 등록하고, event 하나를 ingress한 뒤, Target B가 `DEAD`에
  도달할 때까지 polling하고, replay한 뒤 `SUCCEEDED`를 확인합니다.
- **Load/reliability check**: `scripts/load-check.sh`는 적당한 수준의 concurrent-ingress load
  script입니다(새로운 tooling 없이 순수 `curl` + shell). N개의 concurrent ingress request를
  발사하고 모든 request가 결국 terminal delivery state에 도달하는지 확인하여, 구체적인 처리량
  수치를 주장하는 대신 가벼운 concurrent load 하에서 시스템이 버티는지 여부를 드러냅니다.

## Deliberately out of scope

- hosted/production Grafana나 Prometheus(Compose service는 지금까지 이 프로젝트에 추가된 다른
  모든 infrastructure와 마찬가지로 local dev 전용입니다).
- Alerting rule, SLO, paging 연동 — 개인 프로젝트에는 on-call 체계가 존재하지 않습니다.
- 의미 있는 production 규모의 distributed load testing(예: k6/Gatling cluster) — 여기 있는 load
  script는 성능 benchmark가 아니라 reproducibility/regression check입니다.
- Log aggregation(예: Loki) — SLF4J를 통한 structured logging은 이미 존재하며, 이를 중앙화하는
  작업은 여기서 시도하지 않습니다.

## Acceptance

- `docker compose up -d`로 Postgres, Kafka, Prometheus, Grafana, Zipkin이 모두 기동되고, app이
  이들 전부에 연결됩니다.
- `/actuator/prometheus`가 custom counter들을 노출하고, demo 실행 동안 이 값들이 실제로
  증가하는 것이 눈에 보입니다.
- `scripts/dlq-replay-demo.sh`는 실행 중인 instance를 대상으로 무인(unattended)으로 실행되며,
  failure -> DLQ -> replay 전체 경로가 실제로 완료된 경우에만 exit 0을 반환합니다.
- `scripts/load-check.sh`는 무인으로 실행되며, 발사된 모든 request가 timeout 내에
  terminal delivery state에 도달했는지에 대한 pass/fail을 보고합니다.
- ingress request 하나에 대한 trace가 Zipkin에서 보이며, ingress 호출부터 Target HTTP call(s)
  까지 이어집니다.
