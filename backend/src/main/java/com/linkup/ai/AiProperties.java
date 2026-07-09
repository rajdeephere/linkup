package com.linkup.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Binding of {@code linkup.ai.*} (Day 12, extended Day 13).
 *
 * The AI features call an OpenAI-compatible chat API — Groq by default (free tier, open-source
 * models). {@code enabled=false} uses the deterministic {@code StubAiAssistant} so the app + demo run
 * with no key. Because the wire format is OpenAI-compatible, switching to Ollama / OpenAI is just a
 * base-url + model change.
 *
 * @param enabled            use the real provider ({@link GroqAiAssistant}); false → {@link StubAiAssistant}
 * @param baseUrl            OpenAI-compatible base URL (e.g. https://api.groq.com/openai/v1)
 * @param apiKey             bearer key for the provider
 * @param model              model id (e.g. llama-3.3-70b-versatile)
 * @param maxHistory         max recent messages fed to the model as context
 * @param rateLimitPerMinute per-user cap on on-demand AI calls (429 over it); ≤0 disables (Day 13)
 * @param summaryCacheTtl    how long a cached summary lives before Redis evicts it (Day 13)
 */
@ConfigurationProperties(prefix = "linkup.ai")
public record AiProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        int maxHistory,
        int rateLimitPerMinute,
        Duration summaryCacheTtl
) {
}
