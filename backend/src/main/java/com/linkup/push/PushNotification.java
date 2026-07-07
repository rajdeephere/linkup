package com.linkup.push;

import java.util.UUID;

/**
 * The transport-agnostic notification a {@link PushSender} delivers. Built once per (message,
 * device); carries the display text plus the data a client needs to route the tap and set its
 * badge. Note it deliberately does NOT carry message bytes/media — just a preview + ids.
 */
public record PushNotification(
        UUID messageId,
        UUID conversationId,
        String title,
        String body,
        int unreadCount
) {
}
