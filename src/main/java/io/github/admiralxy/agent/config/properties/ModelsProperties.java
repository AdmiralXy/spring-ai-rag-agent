package io.github.admiralxy.agent.config.properties;

import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.models")
@Getter
@Setter
public class ModelsProperties {

    /**
     * Name of the model. (API)
     */
    @Nonnull
    private String name;

    /**
     * Display name of the model.
     */
    @Nonnull
    private String displayName;

    /**
     * Alias.
     */
    @Nonnull
    private String alias;

    /**
     * Base URL of the model API.
     */
    @Nonnull
    private String baseUrl;

    /**
     * API key for authentication.
     */
    @Nonnull
    private String apiKey;

    /**
     * Properties.
     */
    @Nonnull
    private ModelProperties properties = new ModelProperties();
}
