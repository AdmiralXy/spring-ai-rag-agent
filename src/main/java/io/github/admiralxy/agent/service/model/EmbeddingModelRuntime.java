package io.github.admiralxy.agent.service.model;

import io.github.admiralxy.agent.config.AiHttpClientBuilderFactory;
import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import io.github.admiralxy.agent.repository.EmbeddingModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EmbeddingModelRuntime {

    private static final String SETTINGS_MISSING = "Embedding model settings are missing";

    private final EmbeddingModelRepository repository;
    private final AiHttpClientBuilderFactory httpClientBuilderFactory;

    private volatile List<Object> signature;
    private volatile EmbeddingModel embeddingModel;

    public EmbeddingModel getOrCreateModel() {
        EmbeddingModelEntity settings = repository.findById(EmbeddingModelEntity.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(SETTINGS_MISSING));
        List<Object> current = signatureOf(settings);
        EmbeddingModel cached = embeddingModel;
        if (cached != null && current.equals(signature)) {
            return cached;
        }
        synchronized (this) {
            if (embeddingModel == null || !current.equals(signature)) {
                embeddingModel = create(settings);
                signature = current;
            }
            return embeddingModel;
        }
    }

    public synchronized void invalidate() {
        embeddingModel = null;
        signature = null;
    }

    private EmbeddingModel create(EmbeddingModelEntity settings) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .dimensions(settings.getDimensions())
                .baseUrl(OpenAiBaseUrls.normalize(settings.getBaseUrl()))
                .apiKey(settings.getApiKey())
                .model(settings.getName())
                .build();
        return OpenAiEmbeddingModel.builder()
                .options(options)
                .metadataMode(MetadataMode.NONE)
                .httpClientBuilderCustomizer(httpClientBuilderFactory.openAiHttpClientCustomizer())
                .build();
    }

    private static List<Object> signatureOf(EmbeddingModelEntity settings) {
        return List.of(settings.getProvider(), settings.getBaseUrl(), settings.getApiKey(), settings.getName(),
                settings.getDimensions());
    }
}
