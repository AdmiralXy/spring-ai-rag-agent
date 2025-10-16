package io.github.admiralxy.agent.config.properties;

import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.models.properties")
@Getter
@Setter
public class ModelProperties {

    /**
     * Streaming support flag.
     */
    private boolean streaming = false;

    /**
     * Temperature.
     */
    private Double temperature = 1.0;

    /**
     * Context tokens limit.
     */
    private Integer maxContextTokens = 128000;

    /**
     * Default system prompt.
     */
    @Nullable
    private String systemPrompt;
}
