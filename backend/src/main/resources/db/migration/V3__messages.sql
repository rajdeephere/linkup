-- V3: messages + the per-conversation seq counter (Day 4).
--
-- `seq` is the ordering source of truth (ADR-0002): strictly monotonic and gap-free PER
-- conversation. We assign it server-side by atomically incrementing conversations.last_seq
-- under a row lock, so concurrent sends to the same conversation can't collide or skip.

alter table conversations
    add column last_seq bigint not null default 0;   -- highest seq assigned so far

create table messages (
    id              uuid         primary key,
    conversation_id uuid         not null references conversations(id) on delete cascade,
    sender_id       uuid         not null references users(id),
    client_msg_id   uuid         not null,            -- sender-generated; idempotency key
    seq             bigint       not null,            -- monotonic per conversation
    type            varchar(20)  not null default 'TEXT',
    body            text,
    created_at      timestamptz  not null default now(),
    edited_at       timestamptz,
    deleted_at      timestamptz,
    -- a retried send (same client_msg_id) is a no-op, not a duplicate row
    constraint uq_message_convo_clientid unique (conversation_id, client_msg_id),
    -- seq is unique within a conversation
    constraint uq_message_convo_seq unique (conversation_id, seq)
);

-- "the last N messages in this conversation, in order" — the dominant read.
create index idx_messages_convo_seq on messages (conversation_id, seq);
