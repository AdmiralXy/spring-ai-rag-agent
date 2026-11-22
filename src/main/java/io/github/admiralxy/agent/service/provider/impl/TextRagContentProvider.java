package io.github.admiralxy.agent.service.provider.impl;

import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.domain.ProviderType;
import io.github.admiralxy.agent.service.provider.AbstractChunkingRagContentProvider;
import io.github.admiralxy.agent.service.provider.RagContent;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
import io.github.admiralxy.agent.service.rag.TextChunkerService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TextRagContentProvider extends AbstractChunkingRagContentProvider {

    public TextRagContentProvider(TextChunkerService textChunkerService, RagProperties ragProperties) {
        super(textChunkerService, ragProperties);
    }

    @Override
    public boolean supports(ProviderType providerType) {
        return ProviderType.TEXT == providerType;
    }

    @Override
    protected Mono<RagContent> resolveContent(RagContentRequest request) {
        return Mono.just(new RagContent(null, request.text()));
    }
}
