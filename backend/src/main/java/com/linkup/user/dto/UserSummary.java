package com.linkup.user.dto;

import com.linkup.user.User;

import java.util.UUID;

/** Minimal public view of a user — used to populate the "start a conversation" picker. */
public record UserSummary(UUID id, String username, String displayName) {
    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
