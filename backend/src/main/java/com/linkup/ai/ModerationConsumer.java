package com.linkup.ai;

import com.linkup.events.MessageEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Moderation consumer (Day 13) — the THIRD consumer group on {@code message.created}, alongside the
 * Day-9 logging consumer ({@code linkup-events}) and the Day-11 push consumer ({@code linkup-push}).
 * Its own group ({@code linkup-ai}) means its own offset: moderation can lag, restart, or replay
 * independently of live delivery and push. This is the async half of the AI flagship (the on-demand
 * half is summarize + smart replies).
 */
@Component
public class ModerationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ModerationConsumer.class);

    private final ModerationService moderationService;

    public ModerationConsumer(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @KafkaListener(topics = MessageEventPublisher.TOPIC, groupId = "linkup-ai")
    public void onMessageCreated(String payload) {
        try {
            moderationService.moderate(payload);
        } catch (Exception e) {
            log.warn("moderation failed: {}", e.getMessage());
        }
    }
}
