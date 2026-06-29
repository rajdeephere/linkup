package com.linkup.conversation;

/**
 * DIRECT = exactly 2 participants (a 1:1 chat); GROUP = N participants with a title.
 * Stored as a string so the DB stays readable and reordering never corrupts rows.
 */
public enum ConversationType {
    DIRECT,
    GROUP
}
