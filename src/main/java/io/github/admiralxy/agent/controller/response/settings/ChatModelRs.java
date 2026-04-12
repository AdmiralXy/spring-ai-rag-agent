package io.github.admiralxy.agent.controller.response.settings;

import io.github.admiralxy.agent.entity.ChatModelProvider;

import java.util.UUID;

public record ChatModelRs(
        UUID id,
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
