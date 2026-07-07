package com.linkup.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The FCM/APNs token a device registers so it can be woken while offline (Day 11). */
public record RegisterPushTokenRequest(
        @NotBlank @Size(max = 512) String pushToken
) {
}
