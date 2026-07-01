package com.linkup.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** Idempotency lookup: has this exact send (by clientMsgId) already been stored? */
    Optional<Message> findByConversationIdAndClientMsgId(UUID conversationId, UUID clientMsgId);

    /** Latest page (no cursor) — newest first; caller reverses to ASC for display. */
    List<Message> findByConversationIdOrderBySeqDesc(UUID conversationId, Pageable pageable);

    /** Scroll-back: the page immediately BEFORE `seq` (older), newest-first. */
    List<Message> findByConversationIdAndSeqLessThanOrderBySeqDesc(
            UUID conversationId, long seq, Pageable pageable);

    /** Catch-up/sync: everything AFTER `seq` (newer than last-known), oldest-first. */
    List<Message> findByConversationIdAndSeqGreaterThanOrderBySeqAsc(
            UUID conversationId, long seq, Pageable pageable);
}
