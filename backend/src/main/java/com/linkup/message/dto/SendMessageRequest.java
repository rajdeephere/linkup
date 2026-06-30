package com.linkup.message.dto;

import com.linkup.message.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * The STOMP send payload. The client generates {@code clientMsgId} (a UUID) so the send is
 * idempotent and the client can dedup the echo. {@code type} defaults to TEXT on the wire if
 * omitted by the client; here we require it explicitly for clarity.
 */
public record SendMessageRequest(
        @NotNull UUID clientMsgId,
        @NotNull MessageType type,
        @NotBlank @Size(max = 8000) String body
) {
}
