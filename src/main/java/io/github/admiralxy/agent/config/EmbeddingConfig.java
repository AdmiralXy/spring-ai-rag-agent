package io.github.admiralxy.agent.config;

import io.github.admiralxy.agent.config.properties.AppProperties;
import io.github.admiralxy.agent.embedding.EmbeddingModelProvider;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class EmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(AppProperties appProperties,
                                         List<EmbeddingModelProvider> providers) {
        String activeProvider = appProperties.getEmbedding().getProvider();

        return providers.stream()
                .filter(provider -> provider.provider().equalsIgnoreCase(activeProvider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unsupported embedding provider: " + activeProvider
                ))
                .model();
    }
}
