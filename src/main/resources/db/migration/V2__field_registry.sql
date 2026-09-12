-- Spec 006: Source/Target Field Registry — see specs/006-field-registry/spec.md.
-- field_key, not key: "key" is a reserved word in some SQL dialects (matches the entity comment).

create table source_fields (
    id                  uuid not null primary key,
    source_event_id     uuid not null references source_events (id),
    field_key           varchar(255) not null,
    json_path           varchar(255) not null,
    data_type           varchar(255) not null check (data_type in ('STRING', 'NUMBER', 'BOOLEAN', 'OBJECT', 'ARRAY', 'DATE')),
    description         varchar(255),
    example_value       varchar(255),
    required            boolean not null,
    sensitive           boolean not null,
    status              varchar(255) not null check (status in ('ACTIVE', 'INACTIVE')),
    created_at          timestamp(6) with time zone,
    constraint uk_source_fields_event_key unique (source_event_id, field_key)
);

create table target_fields (
    id                  uuid not null primary key,
    target_id           uuid not null references targets (id),
    field_key           varchar(255) not null,
    data_type           varchar(255) not null check (data_type in ('STRING', 'NUMBER', 'BOOLEAN', 'OBJECT', 'ARRAY', 'DATE')),
    description         varchar(255),
    example_value       varchar(255),
    required            boolean not null,
    sensitive           boolean not null,
    status              varchar(255) not null check (status in ('ACTIVE', 'INACTIVE')),
    created_at          timestamp(6) with time zone,
    constraint uk_target_fields_target_key unique (target_id, field_key)
);
