package io.github.admiralxy.agent.service.model;

import io.github.admiralxy.agent.config.AiHttpClientBuilderFactory;
import io.github.admiralxy.agent.entity.EmbeddingsModelSettingsEntity;
import io.github.admiralxy.agent.repository.EmbeddingsModelSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmbeddingModelRuntime {

    private static final short SINGLETON_ID = 1;

    private final EmbeddingsModelSettingsRepository repository;
    private final AiHttpClientBuilderFactory httpClientBuilderFactory;

    private volatile String signature;
    private volatile EmbeddingModel embeddingModel;

    public EmbeddingModel getOrCreateModel() {
        EmbeddingsModelSettingsEntity settings = repository.findById(SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Embeddings settings are missing"));

        String currentSignature = buildSignature(settings);
        EmbeddingModel cachedModel = embeddingModel;
        if (cachedModel != null && currentSignature.equals(signature)) {
            return cachedModel;
        }

        synchronized (this) {
            if (embeddingModel != null && currentSignature.equals(signature)) {
                return embeddingModel;
            }

            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(settings.getBaseUrl())
                    .apiKey(settings.getApiKey())
                    .restClientBuilder(httpClientBuilderFactory.createRestClientBuilder())
                    .webClientBuilder(httpClientBuilderFactory.createWebClientBuilder())
                    .build();

            OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                    .model(settings.getName())
                    .dimensions(settings.getDimensions())
                    .build();

            embeddingModel = new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
            signature = currentSignature;
            return embeddingModel;
        }
    }

    public synchronized void invalidate() {
        embeddingModel = null;
        signature = null;
    }

    private String buildSignature(EmbeddingsModelSettingsEntity settings) {
        return settings.getProvider()
                + "|" + settings.getBaseUrl()
                + "|" + settings.getApiKey()
                + "|" + settings.getName()
                + "|" + settings.getDimensions();
    }
}
