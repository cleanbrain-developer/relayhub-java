-- TargetEndpoint: the real per-API-call contract (method + path + optional timeout/headers) that
-- was previously flattened directly onto Subscription. See docs/decisions for the domain-model
-- overhaul this belongs to (maintainer request 2026-09-30, Stage 1).
create table target_endpoints (
    id                    uuid not null primary key,
    target_id             uuid not null references targets (id),
    key                   varchar(255) not null,
    name                  varchar(255) not null,
    description           varchar(255) not null,
    status                varchar(255) not null check (status in ('ACTIVE', 'INACTIVE')),
    http_method           varchar(255) not null check (http_method in ('GET', 'POST', 'PUT', 'PATCH', 'DELETE')),
    path                  varchar(255) not null,
    timeout_override_ms   integer,
    headers               varchar(4000),
    created_at            timestamp(6) with time zone,
    constraint uk_target_endpoints_target_key unique (target_id, key)
);

-- Data migration, part 1: one TargetEndpoint per distinct (target_id, target_method, target_path)
-- combination an existing Subscription already references -- this is the real, in-use API
-- contract for that Target, so it's the most trustworthy source to derive endpoints from.
-- key is slugified from "<method>-<path>" (e.g. "post-webhook-flight"), de-duplicated with a
-- numeric suffix on collision within the same Target (two subscriptions sharing one method+path
-- would already have collapsed via the `distinct` below, so a collision here only happens if two
-- *different* paths slugify to the same string, e.g. "/a.b" and "/a-b").
with distinct_endpoints as (
    select distinct target_id, target_method, target_path
    from subscriptions
),
keyed as (
    select
        target_id,
        target_method,
        target_path,
        lower(target_method) || '-' || nullif(
            regexp_replace(regexp_replace(trim(both '/' from target_path), '[^a-zA-Z0-9]+', '-', 'g'), '^-+|-+$', '', 'g'),
            ''
        ) as base_key,
        row_number() over (partition by target_id order by target_method, target_path) as rn
    from distinct_endpoints
),
final_keys as (
    select
        target_id, target_method, target_path,
        case when count(*) over (partition by target_id, coalesce(base_key, 'root')) > 1
             then coalesce(base_key, 'root') || '-' || rn
             else coalesce(base_key, 'root')
        end as endpoint_key
    from keyed
)
insert into target_endpoints (id, target_id, key, name, description, status, http_method, path, created_at)
select
    gen_random_uuid(),
    target_id,
    endpoint_key,
    target_method || ' ' || target_path,
    'Migrated from Subscription''s inline method+path (V7__target_endpoints.sql).',
    'ACTIVE',
    target_method,
    target_path,
    now()
from final_keys;

-- Data migration, part 2: a Target that has registered TargetFields but zero Subscriptions today
-- has no method+path signal to derive an endpoint from at all -- synthesize one placeholder
-- endpoint so V8's field backfill below has somewhere to attach those fields instead of orphaning
-- them. Flagged clearly in its own description for an operator to review/rename later.
insert into target_endpoints (id, target_id, key, name, description, status, http_method, path, created_at)
select
    gen_random_uuid(),
    t.id,
    'default',
    'Default (needs review)',
    'Auto-created by V7__target_endpoints.sql: this Target had registered fields but no ' ||
        'Subscription to derive a real endpoint from. Rename/replace with the actual endpoint.',
    'ACTIVE',
    'POST',
    '/',
    now()
from targets t
where exists (select 1 from target_fields tf where tf.target_id = t.id)
  and not exists (select 1 from target_endpoints te where te.target_id = t.id);
