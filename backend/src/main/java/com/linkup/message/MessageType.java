package com.linkup.message;

/**
 * Message content type. Day 4 ships TEXT; the media kinds light up in Phase 3, SYSTEM is for
 * "X joined", renames, etc. Stored as a string.
 */
public enum MessageType {
    TEXT,
    IMAGE,
    VOICE,
    VIDEO,
    FILE,
    SYSTEM
}
