package com.linkup.message.dto;

import com.linkup.message.Message;

/**
 * The media reference broadcast with a message (Day 10). Carries the {@code blobKey} plus light
 * metadata so a client can lay out the bubble (image dimensions, voice duration) before resolving
 * a presigned GET URL for the actual bytes. Null on the parent response for a TEXT message.
 */
public record MessageAttachment(
        String blobKey,
        String mimeType,
        Long sizeBytes,
        Integer width,
        Integer height,
        Integer durationMs
) {
    /** Build from the entity, or null if the message has no attachment. */
    public static MessageAttachment from(Message m) {
        if (m.getBlobKey() == null) {
            return null;
        }
        return new MessageAttachment(
                m.getBlobKey(),
                m.getMimeType(),
                m.getSizeBytes(),
                m.getWidth(),
                m.getHeight(),
                m.getDurationMs());
    }
}
