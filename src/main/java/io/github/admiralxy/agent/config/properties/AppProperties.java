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

    public List<ModelsProperties> getModels() {
        if (models == null || models.isEmpty()) {
            loadModelsFromExternalFile();
        }
        return models;
    }

    private void loadModelsFromExternalFile() {
        var modelsPath = configPath + "/" + MODELS_CONFIG_FILENAME;

        try {
            Yaml yaml = new Yaml();
            Resource resource = new DefaultResourceLoader().getResource(modelsPath);

            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    ObjectMapper mapper = new ObjectMapper();
                    List<ModelsProperties> modelsProperties = mapper.convertValue(yaml.load(in), new TypeReference<>() {});
                    log.info("Loaded models from: {}", modelsPath);
                    this.models = modelsProperties;
                }
            } else {
                log.warn("Models file not found at: {}, skipping...", modelsPath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load models from " + modelsPath, e);
        }
    }
}
