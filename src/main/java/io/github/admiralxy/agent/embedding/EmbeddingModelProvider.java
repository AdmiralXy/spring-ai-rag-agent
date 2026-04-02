package io.github.admiralxy.agent.embedding;

import org.springframework.ai.embedding.EmbeddingModel;

public interface EmbeddingModelProvider {

    String provider();

    EmbeddingModel model();
}
