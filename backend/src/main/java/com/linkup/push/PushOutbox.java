package com.linkup.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A durable push intent — one row per (message, device) (Day 11, ADR-0008). The unique
 * (message_id, device_id) constraint is the idempotency key: at-least-once Kafka redelivery
 * can replay the same {@code message.created} event, but the second insert conflicts and is
 * skipped, so a device is never notified twice for the same message.
 */
@Entity
@Table(name = "push_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_push_outbox_msg_device",
                columnNames = {"message_id", "device_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushOutbox {

    @Id
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "push_token", length = 512, nullable = false)
    private String pushToken;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(length = 500, nullable = false)
    private String body;

    @Column(name = "unread_count", nullable = false)
    private int unreadCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PushStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}
