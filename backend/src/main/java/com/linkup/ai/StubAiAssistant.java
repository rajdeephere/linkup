package com.linkup.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default AI backend when {@code linkup.ai.enabled=false} (Day 12). It calls no external service —
 * it derives a deterministic answer from the input so the whole feature (endpoints → service →
 * assistant → UI) works, and the e2e demo asserts, with no API key and no network. The real
 * provider ({@link GroqAiAssistant}) is a config-flag drop-in.
 */
@Component
@ConditionalOnProperty(name = "linkup.ai.enabled", havingValue = "false", matchIfMissing = true)
public class StubAiAssistant implements AiAssistant {

    @Override
    public String summarize(List<AiMessage> conversation) {
        if (conversation.isEmpty()) {
            return "No messages to summarize yet.";
        }
        long participants = conversation.stream().map(AiMessage::senderName).distinct().count();
        AiMessage last = conversation.get(conversation.size() - 1);
        return "[stub summary] %d messages between %d participant(s). Most recent — %s: \"%s\". "
                .formatted(conversation.size(), participants, last.senderName(), trim(last.text()))
                + "(Enable linkup.ai for a real AI summary.)";
    }

    @Override
    public List<String> suggestReplies(List<AiMessage> conversation) {
        // Deterministic, context-light suggestions — enough to prove the round trip end-to-end.
        return List.of("Sounds good 👍", "Can you say more about that?", "Let me check and get back to you.");
    }

    private static String trim(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
