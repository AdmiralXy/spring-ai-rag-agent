package io.github.admiralxy.agent.service.model;

import io.github.admiralxy.agent.config.AiHttpClientBuilderFactory;
import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import io.github.admiralxy.agent.exception.ApiException;
import io.github.admiralxy.agent.repository.EmbeddingModelRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedding models of the users, created on first use and rebuilt when the settings change.
 */
@Component
@RequiredArgsConstructor
public class EmbeddingModelRuntime {

    public static final String NOT_CONFIGURED = "The embedding model is not configured. Open Settings → Models → "
            + "Embeddings and fill in the model, base URL and API key.";

    private static final String SETTINGS_MISSING = "Embedding model settings are missing";

    private final EmbeddingModelRepository repository;
    private final AiHttpClientBuilderFactory httpClientBuilderFactory;
    private final Map<UUID, Cached> models = new ConcurrentHashMap<>();

    /**
     * The model of the user together with the settings it was built from.
     */
    public record Embedder(EmbeddingModelEntity settings, EmbeddingModel model) {

        public float[] embed(String text) {
            return check(model.embed(text));
        }

        public List<float[]> embed(List<String> texts) {
            List<float[]> vectors = model.embed(texts);
            vectors.forEach(this::check);
            return vectors;
        }

        private float[] check(float[] vector) {
            if (vector.length != settings.getDimensions()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ("The embedding model returned %d-dimensional "
                        + "vectors, but %d dimensions are configured. Set Dimensions to %d in Settings → Models → "
                        + "Embeddings.").formatted(vector.length, settings.getDimensions(), vector.length));
            }
            return vector;
        }
    }

    public Embedder forUser(UUID userId) {
        EmbeddingModelEntity settings = repository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(SETTINGS_MISSING));
        if (StringUtils.isAnyBlank(settings.getName(), settings.getBaseUrl(), settings.getApiKey())) {
            throw new ApiException(HttpStatus.PRECONDITION_FAILED, NOT_CONFIGURED);
        }
        List<Object> signature = signatureOf(settings);
        Cached cached = models.compute(userId, (id, current) ->
                current != null && current.signature().equals(signature) ? current
                        : new Cached(signature, create(settings)));
        return new Embedder(settings, cached.model());
    }

    public void invalidate(UUID userId) {
        models.remove(userId);
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

    private record Cached(List<Object> signature, EmbeddingModel model) {
    }
}
