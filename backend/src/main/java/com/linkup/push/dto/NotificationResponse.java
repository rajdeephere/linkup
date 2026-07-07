package com.linkup.push.dto;

import com.linkup.push.PushOutbox;
import com.linkup.push.PushStatus;

import java.time.Instant;
import java.util.UUID;

/** A push the user was (or is being) sent — the notification-center view of the outbox. */
public record NotificationResponse(
        UUID messageId,
        UUID conversationId,
        String title,
        String body,
        int unreadCount,
        PushStatus status,
        Instant createdAt,
        Instant sentAt
) {
    public static NotificationResponse from(PushOutbox p) {
        return new NotificationResponse(
                p.getMessageId(),
                p.getConversationId(),
                p.getTitle(),
                p.getBody(),
                p.getUnreadCount(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getSentAt());
    }
}
