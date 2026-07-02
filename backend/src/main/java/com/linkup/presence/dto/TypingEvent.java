package com.linkup.presence.dto;

import java.util.UUID;

/** Server → client typing indicator for a conversation (the client maps userId → name). */
public record TypingEvent(
        UUID conversationId,
        UUID userId,
        boolean typing
) {
}
