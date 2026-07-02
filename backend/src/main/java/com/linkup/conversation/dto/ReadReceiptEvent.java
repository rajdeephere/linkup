package com.linkup.conversation.dto;

import java.util.UUID;

/** Server → client: a participant advanced their read cursor (drives the blue double-tick). */
public record ReadReceiptEvent(
        UUID conversationId,
        UUID userId,
        long lastReadSeq
) {
}
