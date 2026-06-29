package com.linkup.conversation.dto;

import com.linkup.conversation.Participant;
import com.linkup.conversation.ParticipantRole;

import java.util.UUID;

/** A participant as exposed on the wire — identity + role, no internal fields. */
public record ParticipantSummary(
        UUID userId,
        String username,
        String displayName,
        ParticipantRole role
) {
    public static ParticipantSummary from(Participant p) {
        return new ParticipantSummary(
                p.getUser().getId(),
                p.getUser().getUsername(),
                p.getUser().getDisplayName(),
                p.getRole());
    }
}
