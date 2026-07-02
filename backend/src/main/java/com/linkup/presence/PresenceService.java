package com.linkup.presence;

import com.linkup.conversation.ParticipantRepository;
import com.linkup.presence.dto.PresenceResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Presence (online / last-seen) — high-churn, ephemeral state that lives in Redis, never
 * Postgres (hard scenario #9). A user is online if ANY of their devices holds a live
 * WebSocket session, so we track a Redis SET of session ids per user and the user is online
 * iff that set is non-empty. On the last disconnect we stamp last-seen.
 *
 * Presence changes are pushed to the user's "peers" — the people who share a conversation
 * with them — so a contact list lights up in real time.
 *
 * Known Phase-0 limitation: a hard backend crash leaves orphan session ids in Redis (users
 * look online until they reconnect). A heartbeat-refreshed TTL per session fixes that later.
 */
@Service
public class PresenceService {

    private static final String SESSIONS = "ws:sessions:";        // + userId → SET of sessionIds
    private static final String LAST_SEEN = "presence:lastseen:"; // + userId → epoch millis

    private final StringRedisTemplate redis;
    private final ParticipantRepository participants;
    private final SimpMessagingTemplate messagingTemplate;

    public PresenceService(StringRedisTemplate redis,
                           ParticipantRepository participants,
                           SimpMessagingTemplate messagingTemplate) {
        this.redis = redis;
        this.participants = participants;
        this.messagingTemplate = messagingTemplate;
    }

    /** A device connected. If it's the user's first live session, they just came online. */
    public void onConnect(UUID userId, String sessionId) {
        redis.opsForSet().add(SESSIONS + userId, sessionId);
        Long total = redis.opsForSet().size(SESSIONS + userId);
        if (total != null && total == 1L) {
            broadcast(userId, new PresenceResponse(userId, true, null));
        }
    }

    /** A device disconnected. If it was the user's last session, they're now offline. */
    public void onDisconnect(UUID userId, String sessionId) {
        redis.opsForSet().remove(SESSIONS + userId, sessionId);
        Long total = redis.opsForSet().size(SESSIONS + userId);
        if (total == null || total == 0L) {
            Instant now = Instant.now();
            redis.opsForValue().set(LAST_SEEN + userId, Long.toString(now.toEpochMilli()));
            broadcast(userId, new PresenceResponse(userId, false, now));
        }
    }

    public PresenceResponse presenceOf(UUID userId) {
        Long total = redis.opsForSet().size(SESSIONS + userId);
        boolean online = total != null && total > 0L;
        Instant lastSeen = null;
        if (!online) {
            String v = redis.opsForValue().get(LAST_SEEN + userId);
            if (v != null) lastSeen = Instant.ofEpochMilli(Long.parseLong(v));
        }
        return new PresenceResponse(userId, online, lastSeen);
    }

    private void broadcast(UUID userId, PresenceResponse event) {
        for (String peer : participants.findPeerUsernames(userId)) {
            messagingTemplate.convertAndSendToUser(peer, "/queue/presence", event);
        }
    }
}
