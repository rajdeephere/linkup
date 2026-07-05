package com.linkup.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Strongly-typed binding of the {@code linkup.media.*} config tree (Day 10, ADR-0005).
 *
 * @param endpoint            object-store endpoint baked into presigned URLs — MUST be the host
 *                            the browser can reach (it's part of the SigV4 signature)
 * @param region              S3 region (MinIO ignores it, but SigV4 requires one)
 * @param accessKey           object-store access key
 * @param secretKey           object-store secret key
 * @param bucket              the media bucket
 * @param presignTtl          how long a presigned upload/download URL stays valid
 * @param maxUploadBytes      largest upload we'll hand out a presigned PUT for
 * @param allowedContentTypes MIME allowlist we'll presign an upload for
 */
@ConfigurationProperties(prefix = "linkup.media")
public record MediaProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        Duration presignTtl,
        long maxUploadBytes,
        List<String> allowedContentTypes
) {
}
