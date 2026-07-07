package com.linkup.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default push transport in dev / when FCM is off. It doesn't hit any external service — it logs
 * the notification and reports success, so the whole pipeline (consumer → presence dedup → outbox
 * → dispatch) is exercised and provable without a Firebase project. The durable proof is the
 * {@code push_outbox} row it flips to SENT (asserted by the demo via GET /v1/notifications).
 */
@Component
@ConditionalOnProperty(name = "linkup.push.fcm.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public boolean send(String pushToken, PushNotification n) {
        log.info("PUSH → token=…{} | {} — {} (convo={}, unread={})",
                tail(pushToken), n.title(), n.body(), n.conversationId(), n.unreadCount());
        return true;
    }

    private static String tail(String token) {
        return token.length() <= 6 ? token : token.substring(token.length() - 6);
    }
}
