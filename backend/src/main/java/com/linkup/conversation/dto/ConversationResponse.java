package com.linkup.conversation.dto;

import com.linkup.conversation.Conversation;
import com.linkup.conversation.ConversationType;
import com.linkup.conversation.Participant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public projection of a conversation + its participants, plus {@code unreadCount} for the
 * requesting user (conversation.lastSeq − my lastReadSeq, floored at 0).
 */
public record ConversationResponse(
        UUID id,
        ConversationType type,
        String title,
        Instant createdAt,
        Instant lastMessageAt,
        long unreadCount,
        List<ParticipantSummary> participants
) {
    public static ConversationResponse of(Conversation c, List<Participant> participants, long unreadCount) {
        return new ConversationResponse(
                c.getId(),
                c.getType(),
                c.getTitle(),
                c.getCreatedAt(),
                c.getLastMessageAt(),
                unreadCount,
                participants.stream().map(ParticipantSummary::from).toList());
    }
}
