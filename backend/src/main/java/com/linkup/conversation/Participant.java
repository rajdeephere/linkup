package com.linkup.conversation;

import com.linkup.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Membership of one user in one conversation. The unique (conversation, user) constraint
 * means a user can't be added twice.
 *
 * lastReadSeq is the highest message {@code seq} this user has read in the conversation —
 * it drives unread counts and read receipts once messaging lands (Day 4+). Defaulted to 0.
 *
 * Both relations are LAZY: we rarely need the full Conversation/User loaded just to touch
 * a participant row, and the conversation list explicitly fetch-joins the user when it does.
 */
@Entity
@Table(name = "participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_participant_convo_user",
                columnNames = {"conversation_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Participant {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "last_read_seq", nullable = false)
    private long lastReadSeq;

    @Column(name = "muted_until")
    private Instant mutedUntil;
}
