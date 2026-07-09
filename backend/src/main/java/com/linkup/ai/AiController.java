package com.linkup.ai;

import com.linkup.ai.dto.ModerationFlagResponse;
import com.linkup.ai.dto.SuggestRepliesResponse;
import com.linkup.ai.dto.SummaryResponse;
import com.linkup.auth.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * On-demand AI assist + moderation read (Day 12–13). All endpoints are membership-checked via
 * {@link AiService}; the on-demand ones are also rate-limited.
 *
 *   POST /v1/conversations/{id}/summarize        → { summary, cached }
 *   POST /v1/conversations/{id}/suggest-replies  → { suggestions[] }
 *   GET  /v1/conversations/{id}/moderation       → [ { messageId, category, reason } ]
 */
@RestController
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/v1/conversations/{conversationId}/summarize")
    public SummaryResponse summarize(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID conversationId) {
        return aiService.summarize(principal.getId(), conversationId);
    }

    @PostMapping("/v1/conversations/{conversationId}/suggest-replies")
    public SuggestRepliesResponse suggestReplies(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID conversationId) {
        return new SuggestRepliesResponse(aiService.suggestReplies(principal.getId(), conversationId));
    }

    @GetMapping("/v1/conversations/{conversationId}/moderation")
    public List<ModerationFlagResponse> moderation(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID conversationId) {
        return aiService.flags(principal.getId(), conversationId);
    }
}
