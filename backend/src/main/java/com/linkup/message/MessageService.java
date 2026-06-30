package com.linkup.message;

import com.linkup.common.ForbiddenException;
import com.linkup.common.NotFoundException;
import com.linkup.conversation.Conversation;
import com.linkup.conversation.ConversationRepository;
import com.linkup.conversation.ParticipantRepository;
import com.linkup.message.dto.MessageResponse;
import com.linkup.message.dto.SendMessageRequest;
import com.linkup.user.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The send path — the core of the delivery engine (Day 4).
 *
 *   membership check → idempotency check → assign seq (atomic) → persist → broadcast
 *
 * seq assignment uses a PESSIMISTIC_WRITE lock on the conversation row, so concurrent sends
 * to the same conversation are serialized and each message gets a distinct, gap-free seq
 * (ADR-0002). Delivery is fan-out to every participant's per-user queue; the client dedups on
 * clientMsgId so the display is exactly-once even though the sender also receives the echo
 * (ADR-0004).
 */
@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          ParticipantRepository participantRepository,
                          UserRepository userRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public MessageResponse send(UUID senderId, UUID conversationId, SendMessageRequest req) {
        // 1. Authorization: only a participant may send.
        if (!participantRepository.existsByConversation_IdAndUser_Id(conversationId, senderId)) {
            throw new ForbiddenException("You are not a participant of this conversation");
        }

        // 2. Idempotency: a retried send (same clientMsgId) returns the original, no duplicate.
        var existing = messageRepository.findByConversationIdAndClientMsgId(conversationId, req.clientMsgId());
        if (existing.isPresent()) {
            return MessageResponse.from(existing.get());
        }

        // 3. Assign seq atomically under a row lock (the ordering serialization point).
        Conversation convo = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        long seq = convo.getLastSeq() + 1;
        convo.setLastSeq(seq);
        convo.setLastMessageAt(Instant.now());

        // 4. Persist.
        Message message = Message.builder()
                .id(UUID.randomUUID())
                .conversation(convo)
                .sender(userRepository.getReferenceById(senderId))
                .clientMsgId(req.clientMsgId())
                .seq(seq)
                .type(req.type())
                .body(req.body())
                .createdAt(Instant.now())
                .build();
        messageRepository.save(message);

        // 5. Fan out to every participant's per-user queue (includes the sender → echo).
        MessageResponse response = MessageResponse.from(message);
        broadcast(conversationId, response);
        return response;
    }

    private void broadcast(UUID conversationId, MessageResponse response) {
        participantRepository.findByConversationIdFetchUser(conversationId).forEach(p ->
                messagingTemplate.convertAndSendToUser(
                        p.getUser().getUsername(), "/queue/messages", response));
    }
}
