package com.linkup.ai;

/**
 * A single conversation line handed to the AI (Day 12). Deliberately minimal — a display name and
 * the text — so no ids, tokens, or media bytes ever reach the model; media is reduced to a marker.
 *
 * @param senderName who sent it
 * @param mine       whether this line was sent by the requesting user (drives smart-reply framing)
 * @param text       the message text, or a marker like "[photo]" / "[voice note]" for media
 */
public record AiMessage(String senderName, boolean mine, String text) {
}
