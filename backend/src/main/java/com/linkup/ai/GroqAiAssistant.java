package com.linkup.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Real AI backend (Day 12) — active when {@code linkup.ai.enabled=true}. Talks the OpenAI-compatible
 * chat-completions API, so the same code works against Groq (default), Ollama, or OpenAI by changing
 * only {@code base-url} + {@code model}. Deliberately provider-agnostic (a plain {@link RestClient}),
 * not tied to any vendor SDK.
 *
 * Privacy/scope: only display names + text (media reduced to a marker) are sent — never ids, tokens,
 * or media bytes.
 */
@Component
@ConditionalOnProperty(name = "linkup.ai.enabled", havingValue = "true")
public class GroqAiAssistant implements AiAssistant {

    private static final Logger log = LoggerFactory.getLogger(GroqAiAssistant.class);

    private final AiProperties props;
    private final RestClient http;

    public GroqAiAssistant(AiProperties props) {
        this.props = props;
        this.http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .build();
    }

    @Override
    public String summarize(List<AiMessage> conversation) {
        if (conversation.isEmpty()) {
            return "No messages to summarize yet.";
        }
        String system = "You summarize group chat conversations. Reply with 2–3 short sentences "
                + "capturing what was discussed and any open question or decision. No preamble.";
        return chat(system, "Summarize this conversation:\n\n" + transcript(conversation), 300);
    }

    @Override
    public List<String> suggestReplies(List<AiMessage> conversation) {
        if (conversation.isEmpty()) {
            return List.of();
        }
        String system = "You suggest what the user labeled (me) could send next in this chat. "
                + "Return exactly 3 short, natural reply options, one per line, no numbering, no quotes.";
        String out = chat(system, "Conversation:\n\n" + transcript(conversation)
                + "\n\nSuggest 3 replies for (me):", 200);
        return out.lines()
                .map(l -> l.replaceFirst("^\\s*[-*\\d.)]+\\s*", "").trim())  // strip bullets/numbers
                .filter(l -> !l.isBlank())
                .limit(3)
                .collect(Collectors.toList());
    }

    @Override
    public Moderation moderate(String text) {
        if (text == null || text.isBlank()) {
            return Moderation.safe();
        }
        String system = "You are a content moderator. Classify the message for harassment, hate, "
                + "threats, or spam. Reply with EXACTLY one line: \"SAFE\", or "
                + "\"FLAG|<category>|<short reason>\". No other text.";
        String out = chat(system, "Message: " + text, 60).trim();
        if (!out.toUpperCase().startsWith("FLAG")) {
            return Moderation.safe();   // "SAFE", or anything we can't parse → don't over-flag
        }
        String[] parts = out.split("\\|", 3);
        String category = parts.length > 1 ? parts[1].trim() : "flagged";
        String reason = parts.length > 2 ? parts[2].trim() : "Flagged by moderation";
        return Moderation.flag(category, reason);
    }

    /** One OpenAI-compatible chat-completions round trip; returns the assistant text (or a fallback). */
    private String chat(String system, String user, int maxTokens) {
        Map<String, Object> body = Map.of(
                "model", props.model(),
                "temperature", 0.4,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));
        try {
            JsonNode res = http.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String content = res.path("choices").path(0).path("message").path("content").asText("");
            return content.isBlank() ? "AI returned no content." : content.trim();
        } catch (Exception e) {
            log.warn("AI request failed: {}", e.getMessage());
            return "AI is temporarily unavailable.";
        }
    }

    private String transcript(List<AiMessage> conversation) {
        List<String> lines = new ArrayList<>();
        for (AiMessage m : conversation) {
            String who = m.mine() ? m.senderName() + " (me)" : m.senderName();
            lines.add(who + ": " + m.text());
        }
        return String.join("\n", lines);
    }
}
