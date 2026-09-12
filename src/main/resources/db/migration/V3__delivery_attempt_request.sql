-- Captures what was actually sent to the Target, not just what came back -- an attempt's own
-- request was never persisted, only its response, which made it impossible to show a real
-- request/response detail view (maintainer request 2026-09-13).

alter table delivery_attempts add column request_method varchar(255);
alter table delivery_attempts add column request_url varchar(2048);
alter table delivery_attempts add column request_body varchar(32600);
