package com.linkup.ai.dto;

import java.util.List;

/** Up to three AI-suggested reply options for the composer (Day 12). */
public record SuggestRepliesResponse(List<String> suggestions) {
}
