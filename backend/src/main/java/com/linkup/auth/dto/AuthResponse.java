package com.linkup.auth.dto;

import java.util.UUID;

/**
 * Returned by register/login. Carries the access token plus the device id the
 * client just bound to — the client needs that deviceId for the WebSocket handshake
 * and for per-device receipts later.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        String username,
        String displayName,
        UUID deviceId
) {
    public static AuthResponse bearer(String token, long ttlSeconds, UUID userId,
                                      String username, String displayName, UUID deviceId) {
        return new AuthResponse(token, "Bearer", ttlSeconds, userId, username, displayName, deviceId);
    }
}
