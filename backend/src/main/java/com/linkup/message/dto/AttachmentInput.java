package com.linkup.message.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The attachment a client sends with a media message (Day 10). The client has already uploaded
 * the bytes to storage via a presigned PUT; it sends only this reference + metadata. The server
 * re-checks {@code blobKey} + {@code mimeType} (never trusts them blindly) before persisting.
 */
public record AttachmentInput(
        @NotBlank String blobKey,
        @NotBlank String mimeType,
        Long sizeBytes,
        Integer width,
        Integer height,
        Integer durationMs
) {
}
