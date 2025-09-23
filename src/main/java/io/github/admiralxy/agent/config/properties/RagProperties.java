package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
     * RAG max chars to include in the prompt.
     */
    private int maxChars = 4000;

    /**
     * RAG max chars to include in the prompt.
     */
    private int topK = 100;
}
