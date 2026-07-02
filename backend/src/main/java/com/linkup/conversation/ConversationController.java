package com.linkup.conversation;

import com.linkup.auth.AppUserPrincipal;
import com.linkup.conversation.dto.AddMembersRequest;
import com.linkup.conversation.dto.ConversationResponse;
import com.linkup.conversation.dto.CreateConversationRequest;
import com.linkup.conversation.dto.ReadRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Conversation endpoints. Thin HTTP layer — the caller's identity comes from the JWT
 * principal, and all authorization (membership) lives in the service.
 */
@RestController
@RequestMapping("/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CreateConversationRequest request) {
        ConversationResponse created = conversationService.create(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> listMine(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.ok(conversationService.listMine(principal.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getOne(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(conversationService.getOne(principal.getId(), id));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ConversationResponse> addMembers(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddMembersRequest request) {
        return ResponseEntity.ok(conversationService.addMembers(principal.getId(), id, request));
    }

    /** Advance my read cursor → drives unread counts + the blue double-tick for the others. */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ReadRequest request) {
        conversationService.markRead(principal.getId(), id, request.seq());
        return ResponseEntity.noContent().build();
    }
}
