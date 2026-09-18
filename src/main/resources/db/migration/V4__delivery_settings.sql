-- Retry-before-DLQ threshold, configurable from the admin console instead of a hardcoded constant
-- (DeliveryService.MAX_ATTEMPTS) that was invisible to operators and only changeable via a
-- redeploy (maintainer request 2026-09-18). Single-row table -- one global policy, not per-Target/
-- per-Subscription, matching Spec 002's "fixed constant policy applies to every Subscription".
create table delivery_settings (
    id           int         not null primary key check (id = 1),
    max_attempts int         not null,
    updated_at   timestamptz not null
);

insert into delivery_settings (id, max_attempts, updated_at) values (1, 3, now());
