package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmbeddingProperties {

    /**
     * Active embedding API provider name.
     */
    private String provider = "openai-compatible";

    /**
     * API base URL for embedding provider.
     */
    private String baseUrl = "https://api.openai.com";

    /**
     * API key for embedding provider.
     */
    private String apiKey;

    /**
     * Embedding model name.
     */
    private String model = "text-embedding-3-small";


    /**
     * Embedding vector size used by vector store.
     */
    private Integer dimensions = 1536;
}
