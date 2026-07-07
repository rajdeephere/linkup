package com.linkup.push;

import com.linkup.events.MessageEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Push consumer (Day 11) — reads the durable {@code message.created} log in its OWN consumer group
 * ({@code linkup-push}), independent of the Day-9 logging consumer ({@code linkup-events}). Because
 * it's a separate group it gets its own copy of every message and its own offset, so push
 * processing is decoupled from live delivery and can be restarted/replayed on its own.
 *
 * Across pods, the group ensures each message is processed once; the outbox's unique key defends
 * against redelivery within the group. Failures here don't touch the send path — the message is
 * already delivered and durable.
 */
@Component
public class PushConsumer {

    private static final Logger log = LoggerFactory.getLogger(PushConsumer.class);

    private final PushService pushService;

    public PushConsumer(PushService pushService) {
        this.pushService = pushService;
    }

    @KafkaListener(topics = MessageEventPublisher.TOPIC, groupId = "linkup-push")
    public void onMessageCreated(String payload) {
        try {
            pushService.process(payload);
        } catch (Exception e) {
            log.warn("push processing failed: {}", e.getMessage());
        }
    }
}
