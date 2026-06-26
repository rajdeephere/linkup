-- V2: conversations + participants (Day 2).
--
-- A conversation is 'direct' (exactly 2 participants) or 'group' (N). Membership lives
-- in participants, which also carries last_read_seq (drives unread counts + read receipts
-- in later phases) and per-participant role.

create table conversations (
    id              uuid         primary key,
    type            varchar(20)  not null,          -- DIRECT | GROUP
    title           varchar(150),                   -- null for direct; set for group
    created_at      timestamptz  not null default now(),
    last_message_at timestamptz                     -- bumped when a message is sent (Day 4)
);

create table participants (
    id              uuid         primary key,
    conversation_id uuid         not null references conversations(id) on delete cascade,
    user_id         uuid         not null references users(id)         on delete cascade,
    role            varchar(20)  not null default 'MEMBER',  -- MEMBER | ADMIN
    joined_at       timestamptz  not null default now(),
    last_read_seq   bigint       not null default 0,          -- highest seq this user has read
    muted_until     timestamptz,
    -- a user appears at most once per conversation
    constraint uq_participant_convo_user unique (conversation_id, user_id)
);

-- "my conversations" lookups and per-conversation participant lookups are the hot paths.
create index idx_participants_user_id         on participants (user_id);
create index idx_participants_conversation_id on participants (conversation_id);
