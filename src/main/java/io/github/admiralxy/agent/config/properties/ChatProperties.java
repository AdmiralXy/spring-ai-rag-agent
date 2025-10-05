package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.chat")
@Getter
@Setter
public class ChatProperties {

    /**
     * History limit in messages.
     */
    private int historyLimit = 20;

    /**
     * Use streaming responses.
     */
    private boolean streaming = true;

    /**
     * RAG properties.
     */
    private RagProperties rag;
}
