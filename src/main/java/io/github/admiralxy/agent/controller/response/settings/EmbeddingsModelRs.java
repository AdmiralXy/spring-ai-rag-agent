package io.github.admiralxy.agent.controller.response.settings;

import io.github.admiralxy.agent.entity.EmbeddingModelProviderType;

public record EmbeddingsModelRs(
        EmbeddingModelProviderType provider,
        String baseUrl,
        String apiKey,
        String name,
        int dimensions,
        int maxDocumentTokens
) {
}
