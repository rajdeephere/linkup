package com.linkup.media;

import com.linkup.auth.AppUserPrincipal;
import com.linkup.media.dto.DownloadUrlResponse;
import com.linkup.media.dto.PresignUploadRequest;
import com.linkup.media.dto.PresignUploadResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Media presign endpoints (Day 10, ADR-0005). Bytes never flow through here — only references.
 *
 *   POST /v1/media/presign             → presigned PUT + blobKey (client uploads directly)
 *   GET  /v1/media/download-url?key=   → presigned GET (client displays private media)
 */
@RestController
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/v1/media/presign")
    public PresignUploadResponse presign(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody PresignUploadRequest request) {
        return mediaService.presignUpload(principal.getId(), request);
    }

    @GetMapping("/v1/media/download-url")
    public DownloadUrlResponse downloadUrl(@RequestParam("key") String key) {
        return mediaService.presignDownload(key);
    }
}
