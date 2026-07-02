package com.linkup.conversation.dto;

import com.linkup.conversation.Participant;
import com.linkup.conversation.ParticipantRole;

import java.util.UUID;

/**
 * A participant as exposed on the wire — identity + role + read cursor. `lastReadSeq` lets
 * the client compute the double-tick: my message with seq S is "read" by a participant when
 * their lastReadSeq ≥ S.
 */
public record ParticipantSummary(
        UUID userId,
        String username,
        String displayName,
        ParticipantRole role,
        long lastReadSeq
) {
    public static ParticipantSummary from(Participant p) {
        return new ParticipantSummary(
                p.getUser().getId(),
                p.getUser().getUsername(),
                p.getUser().getDisplayName(),
                p.getRole(),
                p.getLastReadSeq());
    }
}
