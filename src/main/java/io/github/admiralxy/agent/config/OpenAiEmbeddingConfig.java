package io.github.admiralxy.agent.config;

import io.github.admiralxy.agent.config.properties.AppProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiEmbeddingConfig {

    private static final String OPENAI_API_KEY_REQUIRED =
            "OpenAI embedding api-key is empty. Set OPENAI_API_KEY or app.embedding.openai.api-key";

    @Bean(name = "openAiEmbeddingModel")
    @ConditionalOnProperty(prefix = "app.embedding.openai", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OpenAiEmbeddingModel openAiEmbeddingModel(AppProperties appProperties) {
        var embedding = appProperties.getEmbedding();
        var openAi = embedding.getOpenai();

        if (StringUtils.isBlank(openAi.getApiKey())) {
            throw new IllegalStateException(OPENAI_API_KEY_REQUIRED);
        }

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(openAi.getBaseUrl())
                .apiKey(openAi.getApiKey())
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(openAi.getModel())
                .dimensions(embedding.getDimensions())
                .build();

        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }
}
