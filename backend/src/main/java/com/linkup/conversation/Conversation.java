package com.linkup.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A conversation — direct (1:1) or group (N). Membership is modelled separately in
 * {@link Participant}; this row holds only conversation-level metadata.
 *
 * lastMessageAt is bumped when a message is sent (Day 4) and is what the conversation
 * list sorts on (most-recent-first).
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationType type;

    /** Null for direct conversations; required for groups. */
    @Column(length = 150)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    /** Highest seq assigned in this conversation; the source for the next message's seq (ADR-0002). */
    @Column(name = "last_seq", nullable = false)
    private long lastSeq;
}
