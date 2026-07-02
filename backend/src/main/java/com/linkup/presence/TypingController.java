package com.linkup.presence;

import com.linkup.auth.AppUserPrincipal;
import com.linkup.conversation.ParticipantRepository;
import com.linkup.presence.dto.TypingEvent;
import com.linkup.presence.dto.TypingRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Duration;
import java.util.UUID;

/**
 * Typing indicators — the most ephemeral state in the system. A "start" sets a short-TTL
 * Redis key and fans a typing=true event to the other participants; "stop" clears it. The
 * TTL means a client that crashes mid-typing auto-clears (never a stuck "…is typing"), and
 * this state NEVER touches Postgres (hard scenario #9). The client also debounces sends and
 * auto-hides after a timeout, so this path stays cheap under heavy churn.
 */
@Controller
public class TypingController {

    private static final Duration TTL = Duration.ofSeconds(6);

    private final SimpMessagingTemplate messagingTemplate;
    private final ParticipantRepository participants;
    private final StringRedisTemplate redis;

    public TypingController(SimpMessagingTemplate messagingTemplate,
                            ParticipantRepository participants,
                            StringRedisTemplate redis) {
        this.messagingTemplate = messagingTemplate;
        this.participants = participants;
        this.redis = redis;
    }

    @MessageMapping("/conversations/{conversationId}/typing")
    public void typing(@DestinationVariable UUID conversationId,
                       @Payload TypingRequest request,
                       Principal principal) {
        UUID userId = ((AppUserPrincipal) ((Authentication) principal).getPrincipal()).getId();
        if (!participants.existsByConversation_IdAndUser_Id(conversationId, userId)) {
            return; // only members can signal typing
        }
        boolean typing = "start".equalsIgnoreCase(request.state());
        String key = "typing:" + conversationId + ":" + userId;
        if (typing) {
            redis.opsForValue().set(key, "1", TTL); // ephemeral, auto-expires
        } else {
            redis.delete(key);
        }
        TypingEvent event = new TypingEvent(conversationId, userId, typing);
        for (String peer : participants.findOtherParticipantUsernames(conversationId, userId)) {
            messagingTemplate.convertAndSendToUser(peer, "/queue/typing", event);
        }
    }
}
