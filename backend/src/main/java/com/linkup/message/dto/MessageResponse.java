package com.linkup.message.dto;

import com.linkup.message.Message;
import com.linkup.message.MessageType;

import java.time.Instant;
import java.util.UUID;

/** The canonical message as broadcast to clients — carries the server-assigned {@code seq}. */
public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        UUID clientMsgId,
        long seq,
        MessageType type,
        String body,
        MessageAttachment attachment,
        Instant createdAt
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getConversation().getId(),
                m.getSender().getId(),
                m.getClientMsgId(),
                m.getSeq(),
                m.getType(),
                m.getBody(),
                MessageAttachment.from(m),
                m.getCreatedAt());
    }
}
