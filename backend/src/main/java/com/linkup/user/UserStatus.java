package com.linkup.user;

/**
 * Lifecycle state of a user account. Stored as a string (not ordinal) so the DB
 * stays readable and reordering the enum never corrupts existing rows.
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED
}
