-- Baseline schema, captured from the Hibernate-generated DDL (ddl-auto: update) this project used
-- through Spec 003, then hand-cleaned (meaningful constraint names instead of Hibernate's auto
-- hashes). See docs/status/current-state.md and ADR-0004 for why Flyway replaced ddl-auto.

create table sources (
    id                      uuid not null primary key,
    key                     varchar(255) not null,
    name                    varchar(255) not null,
    description             varchar(255) not null,
    authentication_config   varchar(255),
    status                  varchar(255) not null check (status in ('ACTIVE', 'INACTIVE')),
    created_at              timestamp(6) with time zone,
    updated_at              timestamp(6) with time zone,
    constraint uk_sources_key unique (key)
);

create table targets (
    id                      uuid not null primary key,
    key                     varchar(255) not null,
    name                    varchar(255) not null,
    description             varchar(255) not null,
    base_url                varchar(255) not null,
    authentication_config   varchar(255),
    status                  varchar(255) not null check (status in ('ACTIVE', 'INACTIVE')),
    created_at              timestamp(6) with time zone,
    updated_at              timestamp(6) with time zone,
    constraint uk_targets_key unique (key)
);

create table source_events (
    id                      uuid not null primary key,
    source_id               uuid not null references sources (id),
    key                     varchar(255) not null,
    name                    varchar(255) not null,
    description             varchar(255) not null,
    resource_type           varchar(255) not null,
    operation               varchar(255) not null check (operation in ('CREATED', 'REPLACED', 'PATCHED', 'DELETED')),
    ingress_method          varchar(255) not null check (ingress_method in ('GET', 'POST', 'PUT', 'PATCH', 'DELETE')),
    ingress_path            varchar(255) not null,
    resource_id_path        varchar(255) not null,
    occurred_at_path        varchar(255),
    idempotency_key_path    varchar(255),
    idempotency_header      varchar(255),
    payload_schema          varchar(32600),
    status                  varchar(255) not null check (status in ('ACTIVE', 'INACTIVE')),
    created_at              timestamp(6) with time zone,
    constraint uk_source_events_ingress_path unique (ingress_path)
);

create table subscriptions (
    id                          uuid not null primary key,
    source_event_id             uuid not null references source_events (id),
    target_id                   uuid not null references targets (id),
    name                        varchar(255) not null,
    description                 varchar(255) not null,
    target_method               varchar(255) not null check (target_method in ('GET', 'POST', 'PUT', 'PATCH', 'DELETE')),
    target_path                 varchar(255) not null,
    target_payload_template     varchar(32600) not null,
    retry_policy                varchar(255),
    status                      varchar(255) not null check (status in ('ACTIVE', 'INACTIVE')),
    created_at                  timestamp(6) with time zone
);

create table events (
    id                  uuid not null primary key,
    source_id           uuid not null,
    source_event_id     uuid not null,
    resource_type       varchar(255) not null,
    resource_id         varchar(255) not null,
    operation           varchar(255) not null check (operation in ('CREATED', 'REPLACED', 'PATCHED', 'DELETED')),
    occurred_at         timestamp(6) with time zone,
    received_at         timestamp(6) with time zone,
    idempotency_key     varchar(255),
    payload              varchar(32600) not null
);

create table deliveries (
    id                  uuid not null primary key,
    event_id            uuid not null,
    subscription_id     uuid not null,
    target_id           uuid not null,
    state               varchar(255) not null check (state in ('PENDING', 'SUCCEEDED', 'DEAD')),
    attempt_count       integer not null,
    created_at          timestamp(6) with time zone,
    updated_at          timestamp(6) with time zone,
    -- Prevents a Kafka-redelivered delivery task from creating a duplicate Delivery for the same
    -- (Event, Subscription) pair. See DeliveryService#deliver and ADR-0004.
    constraint uk_deliveries_event_subscription unique (event_id, subscription_id)
);

create table delivery_attempts (
    id                  uuid not null primary key,
    delivery_id         uuid not null,
    event_id            uuid not null,
    subscription_id     uuid not null,
    target_id           uuid not null,
    attempt_number      integer not null,
    status              varchar(255) not null check (status in ('SUCCESS', 'FAILED')),
    http_status         integer,
    response_body       varchar(32600),
    error_message       varchar(32600),
    attempted_at        timestamp(6) with time zone
);

create table outbox_events (
    id                  uuid not null primary key,
    event_id            uuid not null,
    subscription_id     uuid not null,
    status              varchar(255) not null check (status in ('PENDING', 'PUBLISHED')),
    created_at          timestamp(6) with time zone,
    published_at        timestamp(6) with time zone
);
