package com.linkup.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Async moderation (Day 13). Consumes a {@code message.created} event, classifies the message via the
 * active {@link AiAssistant}, and records the verdict. Only TEXT is moderated (media carries no text
 * here); idempotent on message id so at-least-once redelivery never double-moderates. Failures are
 * swallowed — moderation is a passive overlay and must never disturb delivery.
 */
@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    private final AiAssistant assistant;
    private final MessageModerationRepository repo;
    private final ObjectMapper mapper;

    public ModerationService(AiAssistant assistant,
                             MessageModerationRepository repo,
                             ObjectMapper mapper) {
        this.assistant = assistant;
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional
    public void moderate(String payload) throws Exception {
        JsonNode m = mapper.readTree(payload);
        if (!"TEXT".equals(m.path("type").asText())) {
            return;   // only text is moderated; media has no text to classify
        }
        UUID messageId = UUID.fromString(m.path("id").asText());
        if (repo.existsByMessageId(messageId)) {
            return;   // already moderated (redelivery / rebalance)
        }
        UUID conversationId = UUID.fromString(m.path("conversationId").asText());
        String body = m.path("body").asText("");

        Moderation verdict = assistant.moderate(body);
        repo.save(MessageModeration.builder()
                .id(UUID.randomUUID())
                .messageId(messageId)
                .conversationId(conversationId)
                .flagged(verdict.flagged())
                .category(verdict.category())
                .reason(verdict.reason())
                .checkedAt(Instant.now())
                .build());
        if (verdict.flagged()) {
            log.info("moderation FLAG convo={} msg={} category={}", conversationId, messageId, verdict.category());
        }
    }
}
