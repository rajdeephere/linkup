package com.linkup.common;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error envelope so every failure looks the same on the wire — the Angular
 * client can handle errors generically instead of guessing each endpoint's shape.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors
) {
    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message, Map.of());
    }

    public static ApiError validation(int status, String error, String message,
                                      Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, fieldErrors);
    }
}
