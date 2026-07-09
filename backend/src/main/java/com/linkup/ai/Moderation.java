package com.linkup.ai;

/**
 * The result of moderating one message (Day 13).
 *
 * @param flagged  true if the content should be flagged
 * @param category short label, e.g. "harassment" / "spam" (null when not flagged)
 * @param reason   one-line explanation (null when not flagged)
 */
public record Moderation(boolean flagged, String category, String reason) {

    public static Moderation safe() {
        return new Moderation(false, null, null);
    }

    public static Moderation flag(String category, String reason) {
        return new Moderation(true, category, reason);
    }
}
