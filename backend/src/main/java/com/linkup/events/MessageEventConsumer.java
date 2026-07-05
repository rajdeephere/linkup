package com.linkup.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Day-9 placeholder consumer of the durable message log — proves the async pipeline works.
 *
 * The real consumers grow off this same topic in their own groups: push notifications (Day 11),
 * search indexing, AI assist. Because they're a consumer GROUP, each event is processed once
 * across the pods, and a consumer can be restarted safely (Kafka replays from its offset).
 */
@Component
public class MessageEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageEventConsumer.class);
    private final ObjectMapper mapper;

    public MessageEventConsumer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @KafkaListener(topics = MessageEventPublisher.TOPIC)
    public void onMessageCreated(String payload) {
        try {
            JsonNode m = mapper.readTree(payload);
            log.info("Kafka[message.created] convo={} seq={} sender={}",
                    m.path("conversationId").asText(), m.path("seq").asLong(), m.path("senderId").asText());
        } catch (Exception e) {
            log.warn("Bad message.created payload: {}", e.getMessage());
        }
    }
}
