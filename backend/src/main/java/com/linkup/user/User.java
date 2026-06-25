package com.linkup.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A LinkUp account. Lives in Postgres (relational metadata store, ADR-3).
 *
 * The id is a UUID assigned by the application (not a DB sequence) so that:
 *  - ids are globally unique and non-guessable (no enumerable /users/1, /users/2),
 *  - we can generate the id before the row exists (useful for outbox/event flows later),
 *  - sharding/replication across regions never collides on an auto-increment.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** BCrypt hash — never the plaintext password. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
