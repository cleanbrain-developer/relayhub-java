# Spec 004: Tasks

- [x] Actuator + Micrometer Prometheus registry; expose `/actuator/prometheus`, `/actuator/health`.
- [x] Custom counters/timers in `IngressService`, `OutboxPublisher`, `DeliveryService`.
- [x] `docker-compose.yml`: Prometheus (scrape config) + Grafana (provisioned datasource + dashboard).
- [x] Micrometer Tracing -> OpenTelemetry -> Zipkin; `docker-compose.yml` Zipkin service.
- [x] `scripts/dlq-replay-demo.sh`.
- [x] `scripts/load-check.sh`.
- [x] Manual verification: metrics visible, dashboard renders, trace visible in Zipkin, both scripts pass.

All verified live against the real `docker compose` stack (2026-09-10): `relayhub_*` counters/timer
observed populating in `/actuator/prometheus`; Prometheus `up{job="relayhub"}` = 1 (scrape working);
Grafana's `/api/datasources` and `/api/search` confirmed the provisioned Prometheus datasource and
the "RelayHub" dashboard both auto-loaded; Zipkin's `/api/v2/traces` returned a span named
`http post /ingress/v1/**` for the demo ingress request. Both scripts run to a `PASS` exit code
against a live instance — `dlq-replay-demo.sh` reproduces the full failure -> DLQ -> replay flow,
`load-check.sh` fired 10 concurrent ingress requests and confirmed all reached `SUCCEEDED`.

Two real portability bugs found and fixed while writing the scripts, not just the app: (1) a
Node.js child process (native Windows binary under Git Bash) cannot resolve bash's POSIX-style
temp paths (`/tmp/tmp.XXXX`) — fixed by converting via `cygpath -w` before embedding the path in
generated JS source; (2) a plain `&`/`wait` background-job loop for firing concurrent requests
hung indefinitely under Git Bash on Windows once concurrency went past a handful — replaced with
`xargs -P`, which is more portable.
