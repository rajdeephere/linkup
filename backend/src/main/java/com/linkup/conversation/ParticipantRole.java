package com.linkup.conversation;

/**
 * Role within a conversation. The group creator is ADMIN; everyone else is a MEMBER.
 * (Admin-only actions like rename/remove-member come in a later phase.)
 */
public enum ParticipantRole {
    MEMBER,
    ADMIN
}
