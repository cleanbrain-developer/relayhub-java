-- Auto-replay interval, admin-configurable alongside max_attempts (maintainer request
-- 2026-09-18). NULL means "use the relayhub.dlq.auto-replay-interval-ms default" — the property
-- stays as the fallback (and is what the H2 test profile's 1-hour override still governs, since
-- Flyway doesn't run against H2 -- see ADR-0004) until an operator explicitly overrides it here.
alter table delivery_settings add column auto_replay_interval_ms bigint;
