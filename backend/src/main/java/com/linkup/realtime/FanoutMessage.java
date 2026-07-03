package com.linkup.realtime;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * The envelope published to Redis for cross-pod delivery: deliver {@code payload} to each of
 * {@code recipients} (usernames) on {@code destination} (e.g. "/queue/messages"). Payload is a
 * JsonNode so any DTO round-trips unchanged.
 */
public record FanoutMessage(
        List<String> recipients,
        String destination,
        JsonNode payload
) {
}
