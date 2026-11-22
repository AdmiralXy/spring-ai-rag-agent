package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.UUID;

/**
 * The read-only chat model every user gets, so the project can be tried without an own API key.
 */
@Configuration
@ConfigurationProperties(prefix = "app.builtin-model")
@Getter
@Setter
public class BuiltinModelProperties {

    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private boolean enabled = true;
    private String label = "OSS";
    private String name;
    private String baseUrl;
    private String apiKey;

    /**
     * Markdown file with the system prompt.
     */
    private Resource systemPrompt;

    private boolean streaming = true;
    private double temperature = 1.0;
    private int maxContextTokens = 128_000;
    private int maxOutputTokens;
    private int priority = 1000;
}
