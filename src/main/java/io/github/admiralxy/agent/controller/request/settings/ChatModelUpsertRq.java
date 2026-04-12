package io.github.admiralxy.agent.controller.request.settings;

import io.github.admiralxy.agent.entity.ChatModelProvider;

public record ChatModelUpsertRq(
        ChatModelProvider provider,
        String label,
        String name,
        String baseUrl,
        String apiKey,
        boolean streaming,
        String systemPrompt,
        int priority,
        double temperature,
        int maxContextTokens
) {
}
