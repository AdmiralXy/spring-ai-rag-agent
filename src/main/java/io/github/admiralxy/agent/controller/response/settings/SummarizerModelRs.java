package io.github.admiralxy.agent.controller.response.settings;

import io.github.admiralxy.agent.entity.ChatModelProvider;

public record SummarizerModelRs(
        ChatModelProvider provider,
        String name,
        String baseUrl,
        String apiKey,
        String systemPrompt
) {
}
