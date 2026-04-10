package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.chat.rag")
@Getter
@Setter
public class RagProperties {

    /**
     * RAG context percentage to include in the prompt.
     */
    private int percentage = 30;

    /**
     * RAG top-k documents to include.
     */
    private int topK = 100;

    /**
     * Confluence connection settings for RAG provider.
     */
    @NestedConfigurationProperty
    private ConfluenceProperties confluence = new ConfluenceProperties();
}
