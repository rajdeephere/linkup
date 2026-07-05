package com.linkup.media.dto;

/**
 * A short-lived presigned GET URL for displaying private media (image {@code <img>} / voice
 * {@code <audio>}). The bucket stays private; recipients fetch bytes straight from storage.
 *
 * @param url              presigned GET URL (short-lived)
 * @param expiresInSeconds how long the URL is valid
 */
public record DownloadUrlResponse(
        String url,
        long expiresInSeconds
) {
}
