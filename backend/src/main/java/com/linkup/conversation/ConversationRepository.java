package com.linkup.conversation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Loads the conversation with a PESSIMISTIC_WRITE row lock (SELECT … FOR UPDATE).
     * This is the seq serialization point (ADR-0002): two concurrent sends to the same
     * conversation queue on this lock, so each gets a distinct, gap-free seq.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findByIdForUpdate(@Param("id") UUID id);
}
