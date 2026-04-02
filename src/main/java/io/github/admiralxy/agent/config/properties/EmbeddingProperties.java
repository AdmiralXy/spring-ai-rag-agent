package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmbeddingProperties {

    /**
     * Active embedding API provider name.
     */
    private String provider = "openai";

    /**
     * OpenAI embedding configuration.
     */
    private OpenAiProperties openai = new OpenAiProperties();

    /**
     * Embedding vector size used by vector store.
     */
    private Integer dimensions = 1536;

    @Getter
    @Setter
    public static class OpenAiProperties {

        /**
         * Enables OpenAI embedding auto-configuration.
         */
        private boolean enabled = true;

        /**
         * API base URL.
         */
        private String baseUrl = "https://api.openai.com";

        /**
         * API key.
         */
        private String apiKey;

        /**
         * Embedding model name.
         */
        private String model = "text-embedding-3-small";

    }
}
