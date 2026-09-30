-- Stage 2 of the integration-platform overhaul (maintainer request 2026-09-30): replaces the
-- in-process Thread.sleep retry loop with a non-blocking, DB-persisted retry schedule so a
-- Delivery's backoff wait no longer holds a Kafka consumer thread + DB connection for the whole
-- multi-attempt duration. See DeliveryService/DeliveryRetryScheduler.
--
-- PROCESSING/RETRYING/REPLAYING are new, non-terminal states between the existing PENDING (now
-- genuinely transient — immediately promoted to PROCESSING) and the existing terminal
-- SUCCEEDED/DEAD:
--   PENDING -> PROCESSING -> SUCCEEDED
--   PROCESSING -> RETRYING -> PROCESSING (loop, driven by DeliveryRetryScheduler) -> DEAD
--   DEAD -> REPLAYING -> PROCESSING (manual or auto-sweep replay re-enters the same retry loop,
--     not just one more attempt -- see DeliveryService.replay)
alter table deliveries add column next_attempt_at timestamp(6) with time zone;

alter table deliveries drop constraint deliveries_state_check;
alter table deliveries add constraint deliveries_state_check
    check (state in ('PENDING', 'PROCESSING', 'SUCCEEDED', 'RETRYING', 'DEAD', 'REPLAYING'));
