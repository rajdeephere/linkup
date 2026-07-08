package com.linkup.ai;

import com.linkup.ai.dto.SuggestRepliesResponse;
import com.linkup.ai.dto.SummaryResponse;
import com.linkup.auth.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * On-demand AI assist (Day 12). Both endpoints are user-triggered and membership-checked via
 * {@link AiService}.
 *
 *   POST /v1/conversations/{id}/summarize        → { summary }
 *   POST /v1/conversations/{id}/suggest-replies  → { suggestions[] }
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
        return new SummaryResponse(aiService.summarize(principal.getId(), conversationId));
    }

    @PostMapping("/v1/conversations/{conversationId}/suggest-replies")
    public SuggestRepliesResponse suggestReplies(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID conversationId) {
        return new SuggestRepliesResponse(aiService.suggestReplies(principal.getId(), conversationId));
    }
}
