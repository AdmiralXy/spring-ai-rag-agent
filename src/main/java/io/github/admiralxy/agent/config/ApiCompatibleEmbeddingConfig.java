package io.github.admiralxy.agent.config;

import io.github.admiralxy.agent.config.properties.AppProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiCompatibleEmbeddingConfig {

    private static final String EMBEDDING_BASE_URL_REQUIRED =
            "Embedding base-url is empty. Configure embeddings.baseUrl in models.yaml";

    private static final String EMBEDDING_API_KEY_REQUIRED =
            "Embedding api-key is empty. Configure embeddings.apiKey in models.yaml";

    private static final String EMBEDDING_MODEL_REQUIRED =
            "Embedding model is empty. Configure embeddings.model in models.yaml";

    @Bean(name = "apiCompatibleEmbeddingModel")
    public OpenAiEmbeddingModel apiCompatibleEmbeddingModel(AppProperties appProperties) {
        var embedding = appProperties.getEmbedding();

        if (StringUtils.isBlank(embedding.getBaseUrl())) {
            throw new IllegalStateException(EMBEDDING_BASE_URL_REQUIRED);
        }
        if (StringUtils.isBlank(embedding.getApiKey())) {
            throw new IllegalStateException(EMBEDDING_API_KEY_REQUIRED);
        }
        if (StringUtils.isBlank(embedding.getModel())) {
            throw new IllegalStateException(EMBEDDING_MODEL_REQUIRED);
        }

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(embedding.getBaseUrl())
                .apiKey(embedding.getApiKey())
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embedding.getModel())
                .dimensions(embedding.getDimensions())
                .build();

        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }
}
