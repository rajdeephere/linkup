package com.linkup.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binding of {@code linkup.ai.*} (Day 12).
 *
 * The AI features (thread summarize + smart replies) call an OpenAI-compatible chat API — Groq by
 * default (free tier, open-source models). {@code enabled=false} uses the deterministic
 * {@code StubAiAssistant} so the app + demo run with no key; flip it on + supply a key to use the
 * real provider. Because the wire format is OpenAI-compatible, switching to Ollama / OpenAI / etc.
 * is just a base-url + model change.
 *
 * @param enabled    use the real provider ({@link GroqAiAssistant}); false → {@link StubAiAssistant}
 * @param baseUrl    OpenAI-compatible base URL (e.g. https://api.groq.com/openai/v1)
 * @param apiKey     bearer key for the provider
 * @param model      model id (e.g. llama-3.3-70b-versatile)
 * @param maxHistory max recent messages fed to the model as context
 */
@ConfigurationProperties(prefix = "linkup.ai")
public record AiProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        int maxHistory
) {
}
