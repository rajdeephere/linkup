package com.linkup.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.message.dto.MessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes every stored message to the durable Kafka log ({@code message.created}), keyed by
 * conversation id (so a conversation's events stay ordered within a partition).
 *
 * This is the ASYNC backbone: live delivery (Redis fan-out) is best-effort and ephemeral; the
 * Kafka log is durable and replayable, so async consumers (push, search, AI — later phases)
 * process each message independently of whether a recipient was online. Failing to publish here
 * must NOT fail the send (the message is already persisted), so we log and move on.
 */
@Component
public class MessageEventPublisher {

    static final String TOPIC = "message.created";
    private static final Logger log = LoggerFactory.getLogger(MessageEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public MessageEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    public void publish(MessageResponse message) {
        try {
            kafka.send(TOPIC, message.conversationId().toString(), mapper.writeValueAsString(message));
        } catch (Exception e) {
            // The message is already durably in Postgres; a Kafka hiccup shouldn't fail the send.
            log.warn("Failed to publish message.created (seq={}): {}", message.seq(), e.getMessage());
        }
    }
}
