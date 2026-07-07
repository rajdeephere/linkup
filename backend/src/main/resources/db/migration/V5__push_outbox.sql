-- V5: push outbox (Day 11, ADR-0008).
--
-- A durable "wake this device for this message" record — one row per (message, device). The push
-- consumer (Kafka group `linkup-push`, off `message.created`) writes it; a sender processes it and
-- flips status. Two guarantees live in the schema:
--   * unique (message_id, device_id) — at-least-once Kafka redelivery / a consumer rebalance can
--     replay the same event; the unique key makes the write idempotent (never double-notify).
--   * only OFFLINE recipients get rows at all — an online recipient already got it in-app, so push
--     is skipped entirely (dedup vs in-app delivery, ADR-0008).

create table push_outbox (
    id                uuid         primary key,
    message_id        uuid         not null,
    conversation_id   uuid         not null,
    recipient_user_id uuid         not null references users(id) on delete cascade,
    device_id         uuid         not null references devices(id) on delete cascade,
    push_token        varchar(512) not null,       -- snapshot of the token at enqueue time
    title             varchar(200) not null,
    body              varchar(500) not null,
    unread_count      integer      not null default 0,   -- badge count for this recipient
    status            varchar(20)  not null default 'PENDING',  -- PENDING | SENT | FAILED
    attempts          integer      not null default 0,
    created_at        timestamptz  not null default now(),
    sent_at           timestamptz,
    -- redelivery of the same message to the same device must not create a second notification
    constraint uq_push_outbox_msg_device unique (message_id, device_id)
);

-- "my notifications, newest first" and "the pending backlog" are the two reads.
create index idx_push_outbox_recipient on push_outbox (recipient_user_id, created_at desc);
create index idx_push_outbox_status on push_outbox (status);
