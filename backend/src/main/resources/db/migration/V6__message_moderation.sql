-- V6: AI moderation results (Day 13).
--
-- One row per moderated message — the audit trail for the async moderation pipeline. The
-- `linkup-ai` Kafka consumer (a third consumer group off message.created, alongside Day-9 logging
-- and Day-11 push) writes a row here after classifying each TEXT message. `flagged=false` rows are
-- kept too, so "checked and clean" is distinguishable from "not yet checked". The unique message_id
-- makes at-least-once redelivery idempotent (a message is never moderated/stored twice).

create table message_moderation (
    id              uuid         primary key,
    message_id      uuid         not null unique references messages(id) on delete cascade,
    conversation_id uuid         not null,
    flagged         boolean      not null,
    category        varchar(40),        -- e.g. harassment | spam (null when not flagged)
    reason          varchar(300),       -- one-line explanation (null when not flagged)
    checked_at      timestamptz  not null default now()
);

-- "the flagged messages in this conversation" — the dominant read (moderation overlay).
create index idx_moderation_convo_flagged on message_moderation (conversation_id) where flagged;
