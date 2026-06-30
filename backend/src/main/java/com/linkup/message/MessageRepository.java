package com.linkup.message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** Idempotency lookup: has this exact send (by clientMsgId) already been stored? */
    Optional<Message> findByConversationIdAndClientMsgId(UUID conversationId, UUID clientMsgId);
}
