-- V1: foundational identity tables.
--
-- Schema is owned by Flyway (versioned, reviewable, deterministic) — Hibernate runs
-- in 'validate' mode and only checks its mappings against what's defined here.

create table users (
    id            uuid         primary key,
    username      varchar(50)  not null unique,
    display_name  varchar(100) not null,
    password_hash varchar(100) not null,         -- BCrypt hash, ~60 chars
    status        varchar(20)  not null default 'ACTIVE',
    created_at    timestamptz  not null default now()
);

create table devices (
    id                  uuid         primary key,
    user_id             uuid         not null references users(id) on delete cascade,
    platform            varchar(20)  not null,    -- WEB | IOS | ANDROID
    push_token          varchar(512),             -- FCM/APNs token (Phase 3)
    public_identity_key varchar(512),             -- device public key for E2E (Phase 4)
    last_seen_at        timestamptz,
    created_at          timestamptz  not null default now()
);

-- Fan-out and "my devices" lookups are always by user_id; index it.
create index idx_devices_user_id on devices (user_id);
