package com.linkup.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageModerationRepository extends JpaRepository<MessageModeration, UUID> {

    /** Idempotency guard — has this message already been moderated? */
    boolean existsByMessageId(UUID messageId);

    /** The flagged messages in a conversation (drives the moderation overlay). */
    List<MessageModeration> findByConversationIdAndFlaggedTrue(UUID conversationId);
}
