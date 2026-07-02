package com.linkup.presence.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's presence — used for both the REST lookup and the real-time broadcast.
 * When online, lastSeenAt is null (they're here now); when offline, it's when they left.
 */
public record PresenceResponse(
        UUID userId,
        boolean online,
        Instant lastSeenAt
) {
}
