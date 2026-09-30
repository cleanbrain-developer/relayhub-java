-- Subscription references a TargetEndpoint instead of carrying its own inline
-- target_id/target_method/target_path -- the endpoint now IS the API contract (Stage 1 of the
-- domain-model overhaul, maintainer request 2026-09-30).
alter table subscriptions add column target_endpoint_id uuid references target_endpoints (id);

-- Backfill: lossless by construction -- V7 created exactly one target_endpoints row per distinct
-- (target_id, target_method, target_path) triple that subscriptions itself contained, so every
-- subscription here has exactly one matching endpoint to join to.
update subscriptions s
set target_endpoint_id = te.id
from target_endpoints te
where te.target_id = s.target_id
  and te.http_method = s.target_method
  and te.path = s.target_path;

alter table subscriptions alter column target_endpoint_id set not null;
alter table subscriptions drop column target_id;
alter table subscriptions drop column target_method;
alter table subscriptions drop column target_path;
