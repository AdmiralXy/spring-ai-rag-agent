package io.github.admiralxy.agent.controller.response.settings;

import io.github.admiralxy.agent.entity.EmbeddingModelProvider;

public record EmbeddingsModelRs(
        EmbeddingModelProvider provider,
        String baseUrl,
        String apiKey,
        String name,
        int dimensions,
        int maxDocumentTokens,
        long indexedChunks
) {
}
