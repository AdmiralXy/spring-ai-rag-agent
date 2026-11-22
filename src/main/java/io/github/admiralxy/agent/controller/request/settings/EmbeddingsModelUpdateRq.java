package io.github.admiralxy.agent.controller.request.settings;

import io.github.admiralxy.agent.entity.EmbeddingModelProvider;

public record EmbeddingsModelUpdateRq(
        EmbeddingModelProvider provider,
        String baseUrl,
        String apiKey,
        String name,
        int dimensions,
        int maxDocumentTokens,
        boolean resetDocuments
) {
}
