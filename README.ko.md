> 이 문서는 [`README.md`](README.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# RelayHub (Java)

Event-driven data integration platform입니다. RelayHub는 Source system으로부터 data-change event를 수신하여 내부적으로 표준화한 뒤, 각 Target의 기존 API contract 형태로 Target system에 전달합니다 — 양쪽 어디에도 RelayHub 전용 payload envelope 채택을 요구하지 않습니다. Retry, replay, audit, observability가 기본으로 내장되어 있습니다.

이 repository는 Java 21 / Spring Boot 구현체입니다. 동일한 product design의 다른 언어 구현체가 만들어질 경우, 별도의 sibling repository에 존재합니다 ([`docs/decisions/ADR-0002-java-spring-boot-stack.md`](docs/decisions/ADR-0002-java-spring-boot-stack.md) 참고).

## Status

원래의 design roadmap은 모두 구현되었습니다: vertical slice, Reliability phase(Kafka + Transactional Outbox를 통해 제공되는 Retry/Backoff/DLQ/Replay/Idempotency — `docs/decisions/ADR-0003-incremental-reliability-phase.md` 참고), Flyway migration과 delivery-task idempotency(`docs/decisions/ADR-0004-flyway-and-delivery-dedup.md`), CI, observability(metrics, dashboard, tracing — `specs/004-observability/`). 정확히 무엇이 존재하고, 어떤 known gap이 남아 있으며, 다음에 무엇을 할지는 [`docs/status/current-state.md`](docs/status/current-state.md)를 참고하세요.

## Documentation map

| Topic | Path |
|---|---|
| Project identity | [`PROJECT.yaml`](PROJECT.yaml) |
| Product: problem, users, goals, scope | [`docs/product/`](docs/product/) |
| Architecture: structure, system design | [`docs/architecture/`](docs/architecture/) |
| Accepted decisions | [`docs/decisions/`](docs/decisions/) |
| Current phase and next work | [`docs/status/current-state.md`](docs/status/current-state.md) |
| Durable engineering principles | [`.specify/memory/constitution.md`](.specify/memory/constitution.md) |
| Document ownership / `.ko.md` policy | [`.ai/constitution/documentation-policy.md`](.ai/constitution/documentation-policy.md) |
| Feature specs | [`specs/001-push-event-delivery/`](specs/001-push-event-delivery/), [`specs/002-retry-dlq-replay/`](specs/002-retry-dlq-replay/), [`specs/003-kafka-outbox/`](specs/003-kafka-outbox/), [`specs/004-observability/`](specs/004-observability/) |

## Agent와 함께 작업하기

[`AGENTS.md`](AGENTS.md)에서 시작하세요 — 이 파일은 모든 지원 agent(현재는 Claude Code뿐)를 위한 sole entry point이며, [`docs/architecture/agent-context-model.md`](docs/architecture/agent-context-model.md)에 정의된 순서로 위 문서들로 안내합니다. 이 README, `AGENTS.md`, 그리고 원래의 design conversation은 entry point일 뿐이며, 위의 repository 문서들이 authoritative합니다 (`docs/decisions/ADR-0001-repository-first-context.md` 참고).

## Running locally

```bash
docker compose up -d      # Postgres + Kafka + Prometheus + Grafana + Zipkin
./gradlew bootRun
```

Ingress는 asynchronous합니다: `POST /ingress/v1/...`는 어떤 Target도 호출되기 전에, Event와 그 Outbox row가 durable하게 저장되는 즉시 응답을 반환합니다. delivery 결과를 확인하려면 `GET /api/deliveries?eventId=`를 poll하세요.

`./gradlew test`는 fast path(in-memory H2 + `@EmbeddedKafka`, Docker 불필요)와 `PostgresKafkaIntegrationTest`(Testcontainers, 실제 Postgres + Kafka — **Docker 필요**)를 모두 커버합니다. 후자는 동일한 combined scenario를 실제 infrastructure에 대해 실행하고, `db/migration/V1__init_schema.sql`을 실제로 적용하며, H2 test profile이 아니라 실제 production `application.yml` 설정(`ddl-auto: validate`)을 사용합니다.

## Observability

전체 `docker compose up -d` 스택이 실행 중이고 앱이 시작된 상태에서:

- Metrics: `curl localhost:8080/actuator/prometheus`
- Dashboard: [http://localhost:3000](http://localhost:3000) (Grafana, local dev를 위한 anonymous admin access — "RelayHub" dashboard가 자동으로 provisioning됩니다)
- Traces: [http://localhost:9411](http://localhost:9411) (Zipkin)
- `scripts/dlq-replay-demo.sh` — 실행 중인 instance를 대상으로 한 scripted failure -> DLQ -> replay demo
- `scripts/load-check.sh` — `LOAD_CONCURRENCY`(기본값 20)개의 concurrent ingress request를 발생시키고 모두 `SUCCEEDED` 상태에 도달하는지 확인합니다

두 스크립트 모두 `node`(새로운 project dependency 없이 작은 local echo/toggle Target을 실행하기 위해서만 사용)와 `BASE_URL`(기본값 `http://localhost:8080`)에서 접근 가능한 실행 중인 instance가 필요합니다.
