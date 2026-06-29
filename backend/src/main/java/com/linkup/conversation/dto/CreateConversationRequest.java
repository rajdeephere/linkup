package com.linkup.conversation.dto;

import com.linkup.conversation.ConversationType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Create a conversation. {@code memberUserIds} are the OTHER members — the creator is added
 * automatically. Type-specific rules (direct = exactly 1 other; group needs a title) are
 * enforced in the service, since they're cross-field invariants Bean Validation can't express.
 */
public record CreateConversationRequest(
        @NotNull ConversationType type,
        @Size(max = 150) String title,
        @NotEmpty List<UUID> memberUserIds
) {
}
