-- The DB now backstops ingress idempotency instead of relying solely on the app-level
-- SELECT-then-INSERT check in IngressService, which has a genuine race window between two
-- concurrent requests carrying the same (source_event_id, idempotency_key) (maintainer request
-- 2026-09-30). A partial unique index, not a plain one, since idempotency_key is optional and
-- Postgres already treats each NULL as distinct under a plain unique index -- the WHERE clause
-- makes that explicit rather than relying on that default behavior.
--
-- Defensive cleanup first: a pure duplicate produced by the exact race this migration closes, with
-- zero Deliveries depending on it, is safe to remove before the index is created. If a duplicate
-- WITH Deliveries exists, this deliberately leaves it alone and the CREATE UNIQUE INDEX below will
-- fail -- that would mean the race already produced a consequential duplicate in production, which
-- needs a human look, not a migration silently discarding delivery history.
--
-- row_number(), not min(id): Postgres has no built-in MIN/MAX aggregate for uuid (no default
-- ordering operator class registered for it), even though plain comparison/ORDER BY on uuid works
-- fine -- row_number()'s ORDER BY uses exactly that comparison, so it doesn't hit the same gap.
with ranked as (
    select e.id,
           row_number() over (partition by e.source_event_id, e.idempotency_key order by e.received_at nulls last, e.id) as rn,
           exists (select 1 from deliveries d where d.event_id = e.id) as has_deliveries
    from events e
    where e.idempotency_key is not null
)
delete from events
where id in (select id from ranked where rn > 1 and not has_deliveries);

create unique index uq_events_source_event_idempotency
    on events (source_event_id, idempotency_key)
    where idempotency_key is not null;
