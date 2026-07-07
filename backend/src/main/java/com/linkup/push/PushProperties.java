package com.linkup.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binding of {@code linkup.push.*} (Day 11).
 *
 * @param bodyPreviewMax max chars of a text message shown in a push body (privacy + length)
 * @param fcm            FCM transport config; {@code enabled=false} in dev uses the logging sender
 */
@ConfigurationProperties(prefix = "linkup.push")
public record PushProperties(
        int bodyPreviewMax,
        Fcm fcm
) {
    /**
     * @param enabled     switch the real FCM sender on (off → LoggingPushSender)
     * @param projectId   GCP project id for the FCM HTTP v1 endpoint
     * @param accessToken OAuth2 bearer for FCM. In prod this is minted from a service account via
     *                    google-auth and refreshed; here it's injected so the transport stays a
     *                    drop-in with no extra dependency.
     */
    public record Fcm(
            boolean enabled,
            String projectId,
            String accessToken
    ) {
    }
}
