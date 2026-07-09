package com.linkup.ai.dto;

/** An AI-generated recap of a conversation (Day 12). {@code cached} is true when it was served
 *  from the Redis cache rather than freshly generated (Day 13). */
public record SummaryResponse(String summary, boolean cached) {
}
