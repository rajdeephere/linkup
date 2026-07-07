package com.linkup.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.conversation.Participant;
import com.linkup.conversation.ParticipantRepository;
import com.linkup.presence.PresenceService;
import com.linkup.user.Device;
import com.linkup.user.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Turns a {@code message.created} event into push notifications for OFFLINE recipients (Day 11,
 * ADR-0008). The rules that matter:
 *   - dedup vs in-app: a recipient with a live socket (Redis presence) is skipped — they already
 *     got it in-app; pushing would double-notify.
 *   - idempotent: one outbox row per (message, device); a redelivered event is a no-op.
 *   - never the sender: you don't get pushed for your own message.
 * The outbox is the durable record; dispatch is inline here (a standalone retry-dispatcher is the
 * documented next hardening).
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final ParticipantRepository participants;
    private final DeviceRepository devices;
    private final PresenceService presence;
    private final PushOutboxRepository outbox;
    private final PushSender sender;
    private final PushProperties props;
    private final ObjectMapper mapper;

    public PushService(ParticipantRepository participants,
                       DeviceRepository devices,
                       PresenceService presence,
                       PushOutboxRepository outbox,
                       PushSender sender,
                       PushProperties props,
                       ObjectMapper mapper) {
        this.participants = participants;
        this.devices = devices;
        this.presence = presence;
        this.outbox = outbox;
        this.sender = sender;
        this.props = props;
        this.mapper = mapper;
    }

    @Transactional
    public void process(String payload) throws Exception {
        JsonNode m = mapper.readTree(payload);
        UUID messageId = UUID.fromString(m.path("id").asText());
        UUID conversationId = UUID.fromString(m.path("conversationId").asText());
        UUID senderId = UUID.fromString(m.path("senderId").asText());
        long seq = m.path("seq").asLong();

        List<Participant> members = participants.findByConversationIdFetchUser(conversationId);
        String title = senderName(members, senderId);
        String body = preview(m);

        for (Participant p : members) {
            UUID recipientId = p.getUser().getId();
            if (recipientId.equals(senderId)) {
                continue;   // never push the sender their own message
            }
            if (presence.presenceOf(recipientId).online()) {
                log.debug("push skip (online, in-app covers it): user={} msg={}", recipientId, messageId);
                continue;   // dedup vs in-app delivery
            }
            int unread = (int) Math.max(0, seq - p.getLastReadSeq());
            for (Device device : devices.findByUserIdAndPushTokenIsNotNull(recipientId)) {
                enqueueAndDispatch(device, recipientId, conversationId, messageId, title, body, unread);
            }
        }
    }

    private void enqueueAndDispatch(Device device, UUID recipientId, UUID conversationId,
                                    UUID messageId, String title, String body, int unread) {
        // Idempotency: at-least-once redelivery / a rebalance can replay this event.
        if (outbox.existsByMessageIdAndDeviceId(messageId, device.getId())) {
            return;
        }
        PushOutbox row = PushOutbox.builder()
                .id(UUID.randomUUID())
                .messageId(messageId)
                .conversationId(conversationId)
                .recipientUserId(recipientId)
                .deviceId(device.getId())
                .pushToken(device.getPushToken())
                .title(title)
                .body(body)
                .unreadCount(unread)
                .status(PushStatus.PENDING)
                .attempts(0)
                .createdAt(Instant.now())
                .build();

        PushNotification n = new PushNotification(messageId, conversationId, title, body, unread);
        boolean ok = sender.send(device.getPushToken(), n);
        row.setAttempts(1);
        row.setStatus(ok ? PushStatus.SENT : PushStatus.FAILED);
        if (ok) {
            row.setSentAt(Instant.now());
        }
        outbox.save(row);
    }

    /** Notification title = the sender's display name (fallback if they're not in the fetched set). */
    private String senderName(List<Participant> members, UUID senderId) {
        return members.stream()
                .filter(p -> p.getUser().getId().equals(senderId))
                .map(p -> p.getUser().getDisplayName())
                .findFirst()
                .orElse("New message");
    }

    /** A short, privacy-conscious body — media shows a kind, text is truncated. */
    private String preview(JsonNode m) {
        String type = m.path("type").asText("TEXT");
        return switch (type) {
            case "IMAGE" -> "📷 Photo";
            case "VOICE" -> "🎤 Voice note";
            case "VIDEO" -> "🎬 Video";
            case "FILE" -> "📎 File";
            default -> {
                String body = m.path("body").asText("");
                if (body.isBlank()) yield "New message";
                yield body.length() > props.bodyPreviewMax()
                        ? body.substring(0, props.bodyPreviewMax()) + "…"
                        : body;
            }
        };
    }
}
