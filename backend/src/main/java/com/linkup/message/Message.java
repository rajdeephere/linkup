package com.linkup.message;

import com.linkup.conversation.Conversation;
import com.linkup.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * A message in a conversation.
 *
 * Two invariants are enforced at the DB level:
 *  - unique (conversation, seq)         → seq is the ordering truth, unique per conversation
 *  - unique (conversation, clientMsgId) → a retried send is idempotent, not a duplicate
 *
 * editedAt / deletedAt are tombstone fields for edit/unsend (later phase); null for now.
 */
@Entity
@Table(name = "messages",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_message_convo_clientid",
                        columnNames = {"conversation_id", "client_msg_id"}),
                @UniqueConstraint(name = "uq_message_convo_seq",
                        columnNames = {"conversation_id", "seq"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "client_msg_id", nullable = false)
    private UUID clientMsgId;

    @Column(nullable = false)
    private long seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
