package com.linkup.ai.dto;

import com.linkup.ai.MessageModeration;

import java.util.UUID;

/** A flagged message in a conversation (Day 13) — drives the ⚠️ moderation overlay. */
public record ModerationFlagResponse(UUID messageId, String category, String reason) {
    public static ModerationFlagResponse from(MessageModeration m) {
        return new ModerationFlagResponse(m.getMessageId(), m.getCategory(), m.getReason());
    }
}
