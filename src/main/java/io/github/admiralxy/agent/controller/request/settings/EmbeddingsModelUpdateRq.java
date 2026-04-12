package io.github.admiralxy.agent.controller.request.settings;

import io.github.admiralxy.agent.entity.EmbeddingModelProviderType;

public record EmbeddingsModelUpdateRq(
        EmbeddingModelProviderType provider,
        String baseUrl,
        String apiKey,
        String name,
        int dimensions,
        int maxDocumentTokens
) {
}
