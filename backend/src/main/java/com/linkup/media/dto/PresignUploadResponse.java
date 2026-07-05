package com.linkup.media.dto;

/**
 * The presigned-upload handout. The client PUTs the raw bytes to {@code uploadUrl} with the same
 * {@code Content-Type} it declared, then sends a chat message carrying only {@code blobKey}.
 *
 * @param blobKey          the object key to reference from the message (never the bytes)
 * @param uploadUrl        presigned PUT URL (short-lived); upload goes straight to storage
 * @param contentType      the Content-Type header the client MUST send to match the signature
 * @param expiresInSeconds how long the URL is valid
 */
public record PresignUploadResponse(
        String blobKey,
        String uploadUrl,
        String contentType,
        long expiresInSeconds
) {
}
