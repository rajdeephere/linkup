package com.linkup.push;

/**
 * Transport for waking an offline device (Day 11). Swapping FCM ↔ APNs ↔ a dev logger is a matter
 * of which bean is active — the pipeline (consumer → outbox → dispatch) never changes.
 */
public interface PushSender {

    /**
     * Deliver a notification to one device token.
     *
     * @return true if the transport accepted it (→ outbox SENT), false if it was rejected (→ FAILED)
     */
    boolean send(String pushToken, PushNotification notification);
}
