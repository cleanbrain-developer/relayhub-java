# Spec 004: Observability / Portfolio Quality

## Summary

The last phase in the original design's roadmap (`docs/product/goals.md`, "Long-term direction",
Phase 3): make RelayHub's runtime behavior observable — metrics, tracing, dashboards — and prove
the failure -> DLQ -> replay story with a reproducible demo, plus a basic load/reliability check.
Everything Specs 001-003 built (retry, DLQ, replay, async delivery) is only actually trustworthy if
it can be *seen* operating; this spec is about visibility, not new delivery behavior.

## In scope

- **Metrics**: Spring Boot Actuator + Micrometer, exposed at `/actuator/prometheus`. Custom
  domain counters/timers beyond the JVM/HTTP defaults: ingress events received (by dedup outcome),
  outbox publish attempts, deliveries by terminal state (`SUCCEEDED`/`DEAD`), delivery attempt
  latency, replay invocations.
- **Dashboards**: `docker-compose.yml` gains Prometheus (scraping the app) and Grafana
  (provisioned with the Prometheus datasource and one starter dashboard covering the counters
  above) as local-dev-only services.
- **Tracing**: Micrometer Tracing bridged to OpenTelemetry, exported to a local Zipkin instance
  (added to `docker-compose.yml`) so a single ingress request's trace — ingress -> outbox write ->
  (async gap) -> Kafka consume -> delivery attempt(s) -> Target call — is inspectable end to end.
- **Failure -> DLQ -> Replay demo**: `scripts/dlq-replay-demo.sh`, a reproducible, scripted version
  of the manual verification this session has repeated by hand for Specs 001-003 — registers
  Source/Target A (succeeds)/Target B (fails)/Subscriptions, ingresses one event, polls until
  Target B reaches `DEAD`, replays it, confirms `SUCCEEDED`.
- **Load/reliability check**: `scripts/load-check.sh`, a modest concurrent-ingress load script
  (no new tooling — plain `curl` + shell) that fires N concurrent ingress requests and confirms
  every one eventually reaches a terminal delivery state, surfacing whether the system holds up
  under light concurrent load rather than claiming a specific throughput number.

## Deliberately out of scope

- A hosted/production Grafana or Prometheus (Compose services are for local dev only, matching
  every other piece of infrastructure this project has added so far).
- Alerting rules, SLOs, or paging integration — no on-call story exists for a personal project.
- Distributed load testing at meaningful production scale (e.g. k6/Gatling clusters) — the load
  script here is a reproducibility/regression check, not a performance benchmark.
- Log aggregation (e.g. Loki) — structured logging already exists via SLF4J; centralizing it is not
  attempted here.

## Acceptance

- `docker compose up -d` brings up Postgres, Kafka, Prometheus, Grafana, and Zipkin; the app
  connects to all of them.
- `/actuator/prometheus` exposes the custom counters, and they visibly increment across a demo run.
- `scripts/dlq-replay-demo.sh` runs unattended against a running instance and exits 0 only if the
  full failure -> DLQ -> replay path actually completed.
- `scripts/load-check.sh` runs unattended and reports pass/fail on whether all fired requests
  reached a terminal delivery state within a timeout.
- A trace for one ingress request is visible in Zipkin, spanning the ingress call through to the
  Target HTTP call(s).
