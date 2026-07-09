package com.linkup.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.ai.dto.ModerationFlagResponse;
import com.linkup.ai.dto.SummaryResponse;
import com.linkup.common.ForbiddenException;
import com.linkup.common.NotFoundException;
import com.linkup.common.RateLimitedException;
import com.linkup.conversation.Conversation;
import com.linkup.conversation.ConversationRepository;
import com.linkup.conversation.ParticipantRepository;
import com.linkup.message.MessageType;
import com.linkup.message.MessageService;
import com.linkup.message.dto.MessageResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns a conversation into AI-ready context and delegates to the active {@link AiAssistant}
 * (Day 12), with Redis-backed summary caching + per-user rate limiting (Day 13).
 *
 * Authorization is enforced up front (membership check) so a cache hit can't leak a conversation you
 * aren't in; the miss path also inherits the check from {@link MessageService#history}.
 */
@Service
public class AiService {

    private final MessageService messageService;
    private final ParticipantRepository participants;
    private final ConversationRepository conversations;
    private final MessageModerationRepository moderation;
    private final AiAssistant assistant;
    private final AiProperties props;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public AiService(MessageService messageService,
                     ParticipantRepository participants,
                     ConversationRepository conversations,
                     MessageModerationRepository moderation,
                     AiAssistant assistant,
                     AiProperties props,
                     StringRedisTemplate redis,
                     ObjectMapper mapper) {
        this.messageService = messageService;
        this.participants = participants;
        this.conversations = conversations;
        this.moderation = moderation;
        this.assistant = assistant;
        this.props = props;
        this.redis = redis;
        this.mapper = mapper;
    }

    /** Summary — served from cache while the thread hasn't advanced past the summarized seq. */
    public SummaryResponse summarize(UUID userId, UUID conversationId) {
        requireMember(userId, conversationId);
        rateLimit(userId);

        Conversation convo = conversations.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        long lastSeq = convo.getLastSeq();
        String key = "ai:summary:" + conversationId;

        String cachedJson = redis.opsForValue().get(key);
        if (cachedJson != null) {
            CacheEntry entry = readCache(cachedJson);
            if (entry != null && entry.seq() >= lastSeq) {
                return new SummaryResponse(entry.summary(), true);   // still fresh — no model call
            }
        }

        String summary = assistant.summarize(context(userId, conversationId));
        writeCache(key, new CacheEntry(lastSeq, summary));
        return new SummaryResponse(summary, false);
    }

    public List<String> suggestReplies(UUID userId, UUID conversationId) {
        requireMember(userId, conversationId);
        rateLimit(userId);
        return assistant.suggestReplies(context(userId, conversationId));
    }

    /** The flagged messages in a conversation (membership-checked) — drives the ⚠️ overlay. */
    public List<ModerationFlagResponse> flags(UUID userId, UUID conversationId) {
        requireMember(userId, conversationId);
        return moderation.findByConversationIdAndFlaggedTrue(conversationId).stream()
                .map(ModerationFlagResponse::from)
                .collect(Collectors.toList());
    }

    // --- authz + rate limit ---

    private void requireMember(UUID userId, UUID conversationId) {
        if (!participants.existsByConversation_IdAndUser_Id(conversationId, userId)) {
            throw new ForbiddenException("You are not a participant of this conversation");
        }
    }

    /** Fixed-window per-user limit in Redis; ≤0 disables. Over the cap → 429. */
    private void rateLimit(UUID userId) {
        if (props.rateLimitPerMinute() <= 0) {
            return;
        }
        String key = "ai:rl:" + userId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, java.time.Duration.ofMinutes(1));   // start the window on first hit
        }
        if (count != null && count > props.rateLimitPerMinute()) {
            throw new RateLimitedException("AI rate limit exceeded — try again shortly.");
        }
    }

    // --- cache serialization ---

    private record CacheEntry(long seq, String summary) {}

    private CacheEntry readCache(String json) {
        try {
            return mapper.readValue(json, CacheEntry.class);
        } catch (Exception e) {
            return null;   // corrupt/legacy entry → treat as a miss
        }
    }

    private void writeCache(String key, CacheEntry entry) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(entry), props.summaryCacheTtl());
        } catch (Exception e) {
            // caching is best-effort; a serialization hiccup must not fail the request
        }
    }

    // --- context building ---

    /** The last N messages as {@link AiMessage}s (membership-checked, chronological, names resolved). */
    private List<AiMessage> context(UUID userId, UUID conversationId) {
        List<MessageResponse> messages =
                messageService.history(userId, conversationId, null, null, props.maxHistory()).messages();

        Map<UUID, String> names = participants.findByConversationIdFetchUser(conversationId).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(),
                        p -> p.getUser().getDisplayName(),
                        (a, b) -> a));

        return messages.stream()
                .map(m -> new AiMessage(
                        names.getOrDefault(m.senderId(), "Someone"),
                        m.senderId().equals(userId),
                        textOf(m)))
                .collect(Collectors.toList());
    }

    /** Media is reduced to a marker so no bytes/keys reach the model. */
    private static String textOf(MessageResponse m) {
        if (m.type() == MessageType.TEXT || m.type() == MessageType.SYSTEM) {
            return m.body() == null ? "" : m.body();
        }
        return switch (m.type()) {
            case IMAGE -> "[photo]";
            case VOICE -> "[voice note]";
            case VIDEO -> "[video]";
            case FILE -> "[file]";
            default -> "[attachment]";
        };
    }
}
