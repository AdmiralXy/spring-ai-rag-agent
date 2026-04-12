package io.github.admiralxy.agent.controller.request.settings;

import io.github.admiralxy.agent.entity.ChatModelProvider;

public record SummarizerModelUpdateRq(
        ChatModelProvider provider,
        String name,
        String baseUrl,
        String apiKey,
        String systemPrompt
) {
}
