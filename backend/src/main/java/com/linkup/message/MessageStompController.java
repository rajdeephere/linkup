package com.linkup.message;

import com.linkup.auth.AppUserPrincipal;
import com.linkup.message.dto.SendMessageRequest;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * STOMP send handler. The client SENDs to /app/conversations/{id}/send; the principal is the
 * one bound to the WebSocket session at CONNECT (StompAuthChannelInterceptor), so the sender's
 * identity comes from the authenticated session — never the payload.
 */
@Controller
public class MessageStompController {

    private final MessageService messageService;

    public MessageStompController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/conversations/{conversationId}/send")
    public void send(@DestinationVariable UUID conversationId,
                     @Payload @Valid SendMessageRequest request,
                     Principal principal) {
        messageService.send(currentUserId(principal), conversationId, request);
    }

    private UUID currentUserId(Principal principal) {
        Authentication auth = (Authentication) principal;
        return ((AppUserPrincipal) auth.getPrincipal()).getId();
    }
}
