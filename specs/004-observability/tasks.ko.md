> 이 문서는 [`tasks.md`](tasks.md)의 한국어 번역본입니다. 영어 원본이 canonical이며, 충돌 시 영어 원본이 우선합니다.

# Spec 004: Tasks

**Input**: Design documents from `specs/004-observability/`

- [x] Actuator + Micrometer Prometheus registry; `/actuator/prometheus`, `/actuator/health` 노출.
- [x] `IngressService`, `OutboxPublisher`, `DeliveryService`에 custom counter/timer 추가.
- [x] `docker-compose.yml`: Prometheus(scrape config) + Grafana(provisioned datasource + dashboard).
- [x] Micrometer Tracing -> OpenTelemetry -> Zipkin; `docker-compose.yml`에 Zipkin service 추가.
- [x] `scripts/dlq-replay-demo.sh`.
- [x] `scripts/load-check.sh`.
- [x] 수동 검증: metrics가 보이는지, dashboard가 렌더링되는지, Zipkin에서 trace가 보이는지,
      두 script 모두 통과하는지.

실제 `docker compose` stack을 대상으로 전부 live로 검증됨(2026-09-10): `/actuator/prometheus`에서
`relayhub_*` counter/timer가 채워지는 것을 관찰함; Prometheus의 `up{job="relayhub"}` = 1
(scrape 동작 확인); Grafana의 `/api/datasources`와 `/api/search`로 provisioning된 Prometheus
datasource와 "RelayHub" dashboard가 둘 다 자동으로 로드된 것을 확인함; Zipkin의
`/api/v2/traces`는 demo ingress request에 대해 `http post /ingress/v1/**`라는 이름의 span을
반환함. 두 script 모두 live instance를 대상으로 `PASS` exit code로 실행됨 — `dlq-replay-demo.sh`는
failure -> DLQ -> replay 전체 흐름을 재현하고, `load-check.sh`는 10개의 concurrent ingress
request를 발사하여 모두 `SUCCEEDED`에 도달했음을 확인함.

script를 작성하는 과정에서 app 자체가 아니라 실제로 두 가지 진짜 portability bug가 발견되고
수정되었습니다: (1) Node.js child process(Git Bash 하의 native Windows binary)는 bash의
POSIX 스타일 temp path(`/tmp/tmp.XXXX`)를 해석할 수 없음 — 생성된 JS source에 path를 삽입하기
전에 `cygpath -w`로 변환하여 수정함; (2) concurrent request를 발사하기 위한 평범한
`&`/`wait` 기반 background-job loop가 Windows의 Git Bash에서 concurrency가 몇 개를 넘어가면
무한히 hang됨 — 더 portable한 `xargs -P`로 교체함.
