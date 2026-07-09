package com.linkup.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The moderation verdict for one message (Day 13). The unique {@code message_id} is the idempotency
 * key — a redelivered {@code message.created} event conflicts on insert, so a message is moderated
 * and stored exactly once.
 */
@Entity
@Table(name = "message_moderation",
        uniqueConstraints = @UniqueConstraint(name = "uq_moderation_message", columnNames = "message_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageModeration {

    @Id
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false)
    private boolean flagged;

    @Column(length = 40)
    private String category;

    @Column(length = 300)
    private String reason;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;
}
