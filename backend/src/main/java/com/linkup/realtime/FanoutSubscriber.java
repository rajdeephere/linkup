package com.linkup.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Runs on EVERY pod. Receives fan-out envelopes off the Redis channel and delivers each to the
 * recipients that have a socket on THIS pod (convertAndSendToUser no-ops for users held elsewhere).
 */
@Component
public class FanoutSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(FanoutSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper mapper;

    public FanoutSubscriber(SimpMessagingTemplate messagingTemplate, ObjectMapper mapper) {
        this.messagingTemplate = messagingTemplate;
        this.mapper = mapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            FanoutMessage msg = mapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), FanoutMessage.class);
            for (String recipient : msg.recipients()) {
                messagingTemplate.convertAndSendToUser(recipient, msg.destination(), msg.payload());
            }
        } catch (Exception e) {
            // One malformed frame must not kill the listener.
            log.warn("Dropping bad fan-out frame: {}", e.getMessage());
        }
    }
}
