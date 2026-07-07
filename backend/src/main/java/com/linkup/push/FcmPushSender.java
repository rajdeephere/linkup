package com.linkup.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Real FCM transport (HTTP v1) — active only when {@code linkup.push.fcm.enabled=true}. Kept as a
 * pure drop-in: it POSTs a web-push message to
 * {@code https://fcm.googleapis.com/v1/projects/{projectId}/messages:send} with an OAuth2 bearer.
 *
 * The bearer is injected via config to avoid pulling google-auth into the build; in production you
 * mint it from the service-account JSON (GoogleCredentials → getAccessToken) and refresh it. The
 * pipeline around this class (consumer → outbox → dispatch) is identical either way — this is the
 * only thing that changes to go from dev to real push.
 */
@Component
@ConditionalOnProperty(name = "linkup.push.fcm.enabled", havingValue = "true")
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    private final PushProperties props;
    private final RestClient http;

    public FcmPushSender(PushProperties props) {
        this.props = props;
        this.http = RestClient.builder()
                .baseUrl("https://fcm.googleapis.com/v1/projects/" + props.fcm().projectId())
                .build();
    }

    @Override
    public boolean send(String pushToken, PushNotification n) {
        // FCM HTTP v1 envelope: a `notification` for display + `data` for the app to route/badge.
        Map<String, Object> message = Map.of(
                "token", pushToken,
                "notification", Map.of("title", n.title(), "body", n.body()),
                "data", Map.of(
                        "conversationId", String.valueOf(n.conversationId()),
                        "messageId", String.valueOf(n.messageId()),
                        "unreadCount", String.valueOf(n.unreadCount())));
        try {
            http.post()
                    .uri("/messages:send")
                    .header("Authorization", "Bearer " + props.fcm().accessToken())
                    .body(Map.of("message", message))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("FCM send failed (token …{}): {}",
                    pushToken.length() > 6 ? pushToken.substring(pushToken.length() - 6) : pushToken,
                    e.getMessage());
            return false;
        }
    }
}
