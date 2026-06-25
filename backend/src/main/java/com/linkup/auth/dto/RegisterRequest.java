package com.linkup.auth.dto;

import com.linkup.user.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Bean Validation annotations are enforced by @Valid in the
 * controller, so malformed input is rejected with a 400 before any logic runs.
 *
 * A device is registered at signup because connections are per-device: the very
 * first thing the client will do after auth is open a WebSocket as this device.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotNull Platform platform
) {
}
