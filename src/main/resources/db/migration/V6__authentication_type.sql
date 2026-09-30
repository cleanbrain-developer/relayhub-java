-- Adds a typed authentication strategy alongside the existing free-text authentication_config on
-- Source and Target, so the console can render/validate against a known set instead of a bare
-- string (maintainer request 2026-09-30: integration-platform domain model overhaul, Stage 1).
-- HMAC/OAUTH2/BEARER_TOKEN/BASIC are declared as valid values now so a later Stage doesn't need
-- another migration just to widen this check constraint, but only NONE/API_KEY do anything
-- functionally today -- same as before this migration, authentication was never wired beyond
-- storing a string.
alter table sources add column authentication_type varchar(255) not null default 'NONE'
    check (authentication_type in ('NONE', 'API_KEY', 'HMAC', 'OAUTH2', 'BEARER_TOKEN', 'BASIC'));
alter table sources alter column authentication_type drop default;

alter table targets add column authentication_type varchar(255) not null default 'NONE'
    check (authentication_type in ('NONE', 'API_KEY', 'HMAC', 'OAUTH2', 'BEARER_TOKEN', 'BASIC'));
alter table targets alter column authentication_type drop default;

-- Backfill: a row that already had some authentication_config stored was, in practice, always
-- some kind of key/secret (the only auth this app ever actually read out of that column was a
-- human operator's own free-text note) -- inferring API_KEY for those rows is more accurate than
-- leaving them at NONE.
update sources set authentication_type = 'API_KEY' where authentication_config is not null;
update targets set authentication_type = 'API_KEY' where authentication_config is not null;
