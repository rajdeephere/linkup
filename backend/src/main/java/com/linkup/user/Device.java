package com.linkup.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single device belonging to a user (web tab, phone, etc).
 *
 * Why Device is first-class from day one: real-time delivery fans out to DEVICES,
 * not users. Read receipts, presence, and (later) E2E sessions are all per-device.
 * Modelling it now means the later phases don't require a painful schema migration.
 *
 * - pushToken: FCM/APNs token to wake an offline device (Phase 3).
 * - publicIdentityKey: the device's long-term public key for E2E (Phase 4); the
 *   server only ever stores public keys, never private ones (ADR-6).
 */
@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    private UUID id;

    /** LAZY: we don't want to load the full User every time we touch a Device. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(name = "push_token", length = 512)
    private String pushToken;

    @Column(name = "public_identity_key", length = 512)
    private String publicIdentityKey;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
