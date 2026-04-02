package io.github.admiralxy.agent.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class OpenAiEmbeddingModelProvider implements EmbeddingModelProvider {

    private static final String PROVIDER = "openai";
    private static final String OPENAI_MODEL_NOT_CONFIGURED =
            "OpenAI embedding model bean is not configured. Check spring.ai.openai.api-key and spring.ai.openai.embedding.enabled=true and openai model starter dependency";

    private final ObjectProvider<EmbeddingModel> openAiEmbeddingModel;

    public OpenAiEmbeddingModelProvider(@Qualifier("openAiEmbeddingModel") ObjectProvider<EmbeddingModel> openAiEmbeddingModel) {
        this.openAiEmbeddingModel = openAiEmbeddingModel;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public EmbeddingModel model() {
        return openAiEmbeddingModel.getIfAvailable(() -> {
            throw new IllegalStateException(OPENAI_MODEL_NOT_CONFIGURED);
        });
    }
}
