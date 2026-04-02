package io.github.admiralxy.agent.config.properties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private static final String MODELS_CONFIG_FILENAME = "models.yaml";

    /**
     * Chat properties.
     */
    private ChatProperties chat;

    /**
     * Filepath to models config folder.
     */
    private String configPath;

    /**
     * Models properties.
     */
    private List<ModelsProperties> models;

    /**
     * Embedding provider settings.
     */
    private EmbeddingProperties embedding = new EmbeddingProperties();

    public List<ModelsProperties> getModels() {
        if (models == null || models.isEmpty()) {
            loadModelsFromExternalFile();
        }
        return models;
    }

    public EmbeddingProperties getEmbedding() {
        if (models == null || models.isEmpty()) {
            loadModelsFromExternalFile();
        }
        if (embedding == null) {
            embedding = new EmbeddingProperties();
        }
        return embedding;
    }

    private void loadModelsFromExternalFile() {
        var modelsPath = configPath + "/" + MODELS_CONFIG_FILENAME;

        try {
            Yaml yaml = new Yaml();
            Resource resource = new DefaultResourceLoader().getResource(modelsPath);

            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    ObjectMapper mapper = new ObjectMapper();
                    Object loaded = yaml.load(in);

                    if (loaded instanceof List<?> list) {
                        List<ModelsProperties> modelsProperties = mapper.convertValue(list, new TypeReference<>() {});
                        this.models = modelsProperties;
                    } else if (loaded instanceof Map<?, ?> map) {
                        Object modelsNode = map.get("models");
                        if (modelsNode != null) {
                            List<ModelsProperties> modelsProperties = mapper.convertValue(modelsNode, new TypeReference<>() {});
                            this.models = modelsProperties;
                        }

                        Object embeddingsNode = map.get("embeddings");
                        if (embeddingsNode != null) {
                            this.embedding = mapper.convertValue(embeddingsNode, EmbeddingProperties.class);
                        }
                    }

                    log.info("Loaded models from: {}", modelsPath);
                }
            } else {
                log.warn("Models file not found at: {}, skipping...", modelsPath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load models from " + modelsPath, e);
        }
    }
}
