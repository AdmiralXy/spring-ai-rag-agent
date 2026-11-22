package io.github.admiralxy.agent.service.provider;

import io.github.admiralxy.agent.domain.ProviderType;
import reactor.core.publisher.Flux;

public interface RagContentProvider {

    boolean supports(ProviderType providerType);

    Flux<RagChunk> resolveChunks(RagContentRequest request);
}
