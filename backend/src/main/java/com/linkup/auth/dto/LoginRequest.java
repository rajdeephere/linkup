package com.linkup.auth.dto;

import com.linkup.user.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotNull Platform platform
) {
}
