package com.linkup.ai;

import java.util.List;

/**
 * The AI capability seam (Day 12). One method per on-demand feature. Swapping the real provider for
 * the dev stub — or Groq for Ollama/OpenAI — is a matter of which bean is active; callers never
 * change.
 */
public interface AiAssistant {

    /** A short recap of a conversation ("catch me up"). */
    String summarize(List<AiMessage> conversation);

    /** Up to three short reply options the requesting user could send next. */
    List<String> suggestReplies(List<AiMessage> conversation);

    /** Classify a single message for toxicity/spam (Day 13 — async moderation). */
    Moderation moderate(String text);
}
