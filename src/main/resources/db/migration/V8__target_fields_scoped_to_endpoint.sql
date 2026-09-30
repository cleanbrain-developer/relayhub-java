-- TargetField moves from Target-scoped to TargetEndpoint-scoped, since the same Target system can
-- expose multiple API endpoints with different request shapes (Stage 1 of the domain-model
-- overhaul, maintainer request 2026-09-30). Mirrors SourceField's existing SourceEvent scoping.
alter table target_fields add column target_endpoint_id uuid references target_endpoints (id);

-- Backfill: pre-migration target_fields rows only ever knew their Target, not which specific
-- endpoint (there was no such concept). Duplicating each field onto every TargetEndpoint V7
-- generated for that Target is the safe-over-lossy choice -- nothing is silently dropped, and an
-- operator can prune a field off endpoints it doesn't actually apply to afterward. See V7's own
-- comment for why a Target can end up with more than one endpoint (or exactly one placeholder
-- "default" endpoint if it had fields but no Subscription to derive a real one from).
insert into target_fields (id, target_endpoint_id, field_key, data_type, description, example_value, required, sensitive, status, created_at)
select gen_random_uuid(), te.id, tf.field_key, tf.data_type, tf.description, tf.example_value, tf.required, tf.sensitive, tf.status, tf.created_at
from target_fields tf
join target_endpoints te on te.target_id = tf.target_id;

-- The original rows (target_id set, target_endpoint_id still null) are now superseded by the
-- endpoint-scoped copies inserted above.
delete from target_fields where target_endpoint_id is null;

alter table target_fields alter column target_endpoint_id set not null;
alter table target_fields drop constraint uk_target_fields_target_key;
alter table target_fields drop column target_id;
alter table target_fields add constraint uk_target_fields_endpoint_key unique (target_endpoint_id, field_key);
