package com.linkup.user.dto;

import com.linkup.user.User;
import com.linkup.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of a User for the /me endpoint. Note what's ABSENT: passwordHash
 * never leaves the service layer. DTOs exist precisely so we control the wire shape
 * and never accidentally serialize a sensitive entity field.
 */
public record MeResponse(
        UUID id,
        String username,
        String displayName,
        UserStatus status,
        Instant createdAt
) {
    public static MeResponse from(User user) {
        return new MeResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus(),
                user.getCreatedAt());
    }
}
