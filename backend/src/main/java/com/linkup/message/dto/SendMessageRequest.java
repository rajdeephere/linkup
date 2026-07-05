package com.linkup.message.dto;

import com.linkup.message.MessageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * The STOMP send payload. The client generates {@code clientMsgId} (a UUID) so the send is
 * idempotent and the client can dedup the echo.
 *
 * A TEXT message carries {@code body}; a media message (IMAGE/VOICE/VIDEO/FILE) carries an
 * {@code attachment} reference instead (Day 10). Exactly which is required is enforced in
 * {@code MessageService} based on {@code type}, since it's a cross-field rule.
 */
public record SendMessageRequest(
        @NotNull UUID clientMsgId,
        @NotNull MessageType type,
        @Size(max = 8000) String body,
        @Valid AttachmentInput attachment
) {
}
