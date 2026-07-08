package com.linkup.ai;

import com.linkup.conversation.Participant;
import com.linkup.conversation.ParticipantRepository;
import com.linkup.message.MessageType;
import com.linkup.message.MessageService;
import com.linkup.message.dto.MessageResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a conversation into AI-ready context and delegates to the active {@link AiAssistant} (Day 12).
 * Authorization is inherited from {@link MessageService#history} (throws if the caller isn't a
 * participant), so the AI endpoints can't be used to read a conversation you're not in.
 */
@Service
public class AiService {

    private final MessageService messageService;
    private final ParticipantRepository participants;
    private final AiAssistant assistant;
    private final AiProperties props;

    public AiService(MessageService messageService,
                     ParticipantRepository participants,
                     AiAssistant assistant,
                     AiProperties props) {
        this.messageService = messageService;
        this.participants = participants;
        this.assistant = assistant;
        this.props = props;
    }

    public String summarize(UUID userId, UUID conversationId) {
        return assistant.summarize(context(userId, conversationId));
    }

    public List<String> suggestReplies(UUID userId, UUID conversationId) {
        return assistant.suggestReplies(context(userId, conversationId));
    }

    /** The last N messages as {@link AiMessage}s (membership-checked, chronological, names resolved). */
    private List<AiMessage> context(UUID userId, UUID conversationId) {
        // history() does the membership check and returns messages oldest-first.
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
