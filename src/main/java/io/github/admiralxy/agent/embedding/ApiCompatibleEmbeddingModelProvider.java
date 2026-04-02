package io.github.admiralxy.agent.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class ApiCompatibleEmbeddingModelProvider implements EmbeddingModelProvider {

    private static final String PROVIDER = "openai-compatible";
    private static final String EMBEDDING_MODEL_NOT_CONFIGURED =
            "API-compatible embedding model bean is not configured. Check models.yaml and mark model with embedding: true";

    private final ObjectProvider<EmbeddingModel> embeddingModel;

    public ApiCompatibleEmbeddingModelProvider(@Qualifier("apiCompatibleEmbeddingModel") ObjectProvider<EmbeddingModel> embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public EmbeddingModel model() {
        return embeddingModel.getIfAvailable(() -> {
            throw new IllegalStateException(EMBEDDING_MODEL_NOT_CONFIGURED);
        });
    }
}
