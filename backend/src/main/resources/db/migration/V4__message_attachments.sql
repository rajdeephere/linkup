-- V4: media attachments on messages (Day 10, ADR-0005).
--
-- A media message (IMAGE / VOICE / VIDEO / FILE) carries a REFERENCE to an object in blob
-- storage, never the bytes. `body` stays for TEXT (and an optional caption later); all attachment
-- columns are nullable so TEXT messages are unaffected. The bytes live in S3/MinIO; here we keep
-- only the key + light metadata needed to render without a round trip (dimensions, duration).

alter table messages
    add column blob_key    text,          -- object key in the media bucket (null for TEXT)
    add column mime_type   varchar(120),  -- e.g. image/png, audio/webm
    add column size_bytes  bigint,        -- byte size of the object
    add column width       integer,       -- image/video pixel width  (null if n/a)
    add column height      integer,       -- image/video pixel height (null if n/a)
    add column duration_ms integer;       -- voice/video length in ms  (null if n/a)
