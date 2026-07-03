package com.linkup.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Cross-pod real-time delivery (ADR-0001).
 *
 * The problem: with a stateful WebSocket tier, user A's socket may live on pod-1 while user B's
 * lives on pod-7. A pod's in-memory broker only knows its OWN sockets, so a direct
 * convertAndSendToUser can't reach a recipient on another pod.
 *
 * The fix: instead of delivering directly, PUBLISH the payload + its recipient usernames to a
 * Redis channel. Every pod subscribes (see FanoutSubscriber); each pod delivers to whichever of
 * the recipients have a socket on IT (convertAndSendToUser is a no-op for users it doesn't hold).
 * Since a user's session is on exactly one pod, every recipient is delivered to exactly once —
 * including the sender's echo. Redis is fire-and-forget; durability stays in Postgres/Kafka.
 */
@Service
public class RealtimeFanout {

    static final String CHANNEL = "linkup:fanout";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RealtimeFanout(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /** Publish {@code payload} for delivery to {@code recipients} (usernames) on {@code destination}. */
    public void send(List<String> recipients, String destination, Object payload) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        try {
            FanoutMessage message = new FanoutMessage(recipients, destination, mapper.valueToTree(payload));
            redis.convertAndSend(CHANNEL, mapper.writeValueAsString(message));
        } catch (Exception e) {
            throw new IllegalStateException("Fan-out publish failed", e);
        }
    }
}
