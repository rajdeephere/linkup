package com.linkup.message;

import com.linkup.common.ForbiddenException;
import com.linkup.common.NotFoundException;
import com.linkup.conversation.Conversation;
import com.linkup.conversation.ConversationRepository;
import com.linkup.conversation.ParticipantRepository;
import com.linkup.message.dto.MessageHistoryResponse;
import com.linkup.message.dto.MessageResponse;
import com.linkup.message.dto.SendMessageRequest;
import com.linkup.realtime.RealtimeFanout;
import com.linkup.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final RealtimeFanout fanout;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          ParticipantRepository participantRepository,
                          UserRepository userRepository,
                          RealtimeFanout fanout) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.fanout = fanout;
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

    /**
     * Cursor-paginated history / sync. Always returns messages in ascending seq order.
     *  - after != null   → messages with seq &gt; after (reconnect catch-up, oldest-first)
     *  - before != null  → the page with seq &lt; before (scroll-back), fetched newest-first then reversed
     *  - neither         → the latest page
     * `hasMore` = the page came back full, so another page likely exists in that direction.
     */
    @Transactional(readOnly = true)
    public MessageHistoryResponse history(UUID userId, UUID conversationId,
                                          Long before, Long after, int limit) {
        if (!participantRepository.existsByConversation_IdAndUser_Id(conversationId, userId)) {
            throw new ForbiddenException("You are not a participant of this conversation");
        }
        int capped = Math.max(1, Math.min(limit, 100));
        Pageable page = PageRequest.of(0, capped);

        List<Message> rows;
        if (after != null) {
            rows = messageRepository.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(conversationId, after, page);
        } else if (before != null) {
            rows = new ArrayList<>(messageRepository.findByConversationIdAndSeqLessThanOrderBySeqDesc(conversationId, before, page));
            Collections.reverse(rows); // DESC → ASC for display
        } else {
            rows = new ArrayList<>(messageRepository.findByConversationIdOrderBySeqDesc(conversationId, page));
            Collections.reverse(rows);
        }
        boolean hasMore = rows.size() == capped;
        return new MessageHistoryResponse(rows.stream().map(MessageResponse::from).toList(), hasMore);
    }

    private void broadcast(UUID conversationId, MessageResponse response) {
        // Cross-pod fan-out (ADR-0001): publish to Redis; each pod delivers to its local sockets.
        fanout.send(participantRepository.findParticipantUsernames(conversationId), "/queue/messages", response);
    }
}
