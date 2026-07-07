package com.linkup.media;

import com.linkup.common.BadRequestException;
import com.linkup.media.dto.DownloadUrlResponse;
import com.linkup.media.dto.PresignUploadRequest;
import com.linkup.media.dto.PresignUploadResponse;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.util.Set;
import java.util.UUID;

/**
 * Media presigning (Day 10, ADR-0005). The app never sees the bytes: it validates the request,
 * then hands out a short-lived presigned PUT (upload) or GET (display) so the client talks to
 * object storage directly. This keeps the chat path tiny (a message carries only a blobKey) and
 * lets uploads scale on storage, not on the API tier.
 */
@Service
public class MediaService {

    private static final String KEY_PREFIX = "media/";

    private final S3Presigner presigner;
    private final MediaProperties props;
    private final Set<String> allowed;

    public MediaService(S3Presigner presigner, MediaProperties props) {
        this.presigner = presigner;
        this.props = props;
        this.allowed = Set.copyOf(props.allowedContentTypes().stream()
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .toList());
    }

    /** Validate the declared type/size, then mint a presigned PUT and the key to reference. */
    public PresignUploadResponse presignUpload(UUID userId, PresignUploadRequest req) {
        String contentType = req.contentType().trim().toLowerCase();
        // Match on the base type so codec-parameterized types pass (e.g. a Chrome voice note is
        // "audio/webm;codecs=opus" but the allowlist lists "audio/webm"). We still sign/store the
        // full type below so playback gets the exact Content-Type back.
        if (!allowed.contains(baseType(contentType))) {
            throw new BadRequestException("Unsupported media type: " + req.contentType());
        }
        if (req.sizeBytes() > props.maxUploadBytes()) {
            throw new BadRequestException(
                    "File too large: " + req.sizeBytes() + " > " + props.maxUploadBytes() + " bytes");
        }

        // Namespaced, collision-proof key; the sanitized original suffix stays for readability.
        String blobKey = KEY_PREFIX + userId + "/" + UUID.randomUUID() + "/" + sanitize(req.filename());

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(blobKey)
                .contentType(contentType)   // signed → the client MUST send the same header
                .build();
        PutObjectPresignRequest presign = PutObjectPresignRequest.builder()
                .signatureDuration(props.presignTtl())
                .putObjectRequest(put)
                .build();

        String url = presigner.presignPutObject(presign).url().toString();
        return new PresignUploadResponse(blobKey, url, contentType, props.presignTtl().toSeconds());
    }

    /** Mint a short-lived presigned GET so a client can display private media. */
    public DownloadUrlResponse presignDownload(String blobKey) {
        if (blobKey == null || !blobKey.startsWith(KEY_PREFIX)) {
            throw new BadRequestException("Invalid media key");
        }
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(props.bucket())
                .key(blobKey)
                .build();
        GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                .signatureDuration(props.presignTtl())
                .getObjectRequest(get)
                .build();

        String url = presigner.presignGetObject(presign).url().toString();
        return new DownloadUrlResponse(url, props.presignTtl().toSeconds());
    }

    /** Whether a message-attachment's declared MIME type is one we accept (base type, params ignored). */
    public boolean isAllowedContentType(String contentType) {
        return contentType != null && allowed.contains(baseType(contentType));
    }

    /** The MIME type without parameters, lowercased — "audio/webm;codecs=opus" → "audio/webm". */
    private static String baseType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String c = contentType.trim().toLowerCase();
        int semi = c.indexOf(';');
        return semi >= 0 ? c.substring(0, semi).trim() : c;
    }

    /** Keep a filesystem/URL-safe suffix; the UUID segment guarantees uniqueness regardless. */
    private static String sanitize(String filename) {
        String cleaned = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() > 80 ? cleaned.substring(cleaned.length() - 80) : cleaned;
    }
}
