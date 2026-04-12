package io.github.admiralxy.agent.service.provider;

import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import reactor.core.publisher.Flux;

public interface RagContentProvider {

    /**
     * Checks if this provider handles the given provider type.
     *
     * @param providerType provider type
     * @return true if supported
     */
    boolean supports(ProviderType providerType);

    /**
     * Resolves request into chunks for indexing.
     * Each emitted element will be saved as a separate chunk in vector store.
     *
     * @param request request payload for provider
     * @return stream of chunks
     */
    Flux<RagChunk> resolveChunks(RagContentRequest request);
}
