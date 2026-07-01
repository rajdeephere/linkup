package com.linkup.message;

import com.linkup.auth.AppUserPrincipal;
import com.linkup.message.dto.MessageHistoryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST history/sync for a conversation's messages (Day 6). The live path is STOMP; this is
 * the durable read: load a conversation's history on open, page older on scroll-back, and
 * catch up after a reconnect.
 *
 *   GET /v1/conversations/{id}/messages?limit=50           → latest page
 *   GET /v1/conversations/{id}/messages?before=<seq>&limit → older page (scroll-back)
 *   GET /v1/conversations/{id}/messages?after=<seq>&limit  → missed messages (sync)
 */
@RestController
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/v1/conversations/{conversationId}/messages")
    public MessageHistoryResponse history(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID conversationId,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Long after,
            @RequestParam(defaultValue = "50") int limit) {
        return messageService.history(principal.getId(), conversationId, before, after, limit);
    }
}
