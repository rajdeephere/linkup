package com.linkup.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Ask for a presigned upload. The client declares what it's about to PUT so we can validate the
 * content-type against the allowlist and the size against the cap BEFORE handing out a URL.
 *
 * @param filename    original name, used only to preserve a human-friendly suffix in the key
 * @param contentType MIME type the client will send as the PUT's Content-Type header
 * @param sizeBytes   declared byte size (also enforced by the storage cap on upload)
 */
public record PresignUploadRequest(
        @NotBlank String filename,
        @NotBlank String contentType,
        @Positive long sizeBytes
) {
}
