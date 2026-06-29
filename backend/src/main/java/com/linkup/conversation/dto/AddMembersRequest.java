package com.linkup.conversation.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record AddMembersRequest(
        @NotEmpty List<UUID> memberUserIds
) {
}
