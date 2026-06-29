package com.linkup.conversation.dto;

import com.linkup.conversation.Conversation;
import com.linkup.conversation.ConversationType;
import com.linkup.conversation.Participant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public projection of a conversation + its participants. (Unread counts arrive once
 * messaging + lastReadSeq are wired up in Day 4+.)
 */
public record ConversationResponse(
        UUID id,
        ConversationType type,
        String title,
        Instant createdAt,
        Instant lastMessageAt,
        List<ParticipantSummary> participants
) {
    public static ConversationResponse of(Conversation c, List<Participant> participants) {
        return new ConversationResponse(
                c.getId(),
                c.getType(),
                c.getTitle(),
                c.getCreatedAt(),
                c.getLastMessageAt(),
                participants.stream().map(ParticipantSummary::from).toList());
    }
}
