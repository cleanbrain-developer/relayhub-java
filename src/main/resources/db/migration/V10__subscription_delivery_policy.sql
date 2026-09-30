-- Structured, per-Subscription delivery policy replaces the free-text retry_policy column, which
-- was stored but never read by any delivery code (confirmed dead data). All nullable: null means
-- "use the delivery_settings global default" -- see DeliverySettingsService. Stage 1 only adds
-- these columns/the API surface; DeliveryService itself doesn't read them yet (that's Stage 2).
-- filter_expression is likewise stored-not-evaluated in Stage 1 (maintainer explicitly asked not
-- to over-build a rule engine for this).
alter table subscriptions add column max_attempts integer;
alter table subscriptions add column initial_backoff_ms integer;
alter table subscriptions add column max_backoff_ms integer;
-- double precision, not numeric: Hibernate maps a Java Double to SQL float8/double precision by
-- default, and ddl-auto: validate (outside the test profile) requires an exact column-type match.
alter table subscriptions add column backoff_multiplier double precision;
alter table subscriptions add column jitter boolean;
alter table subscriptions add column timeout_ms integer;
alter table subscriptions add column filter_expression varchar(1000);

alter table subscriptions drop column retry_policy;
