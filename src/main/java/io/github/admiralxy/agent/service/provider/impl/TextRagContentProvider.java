package io.github.admiralxy.agent.service.provider.impl;

import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.service.TextChunkerService;
import io.github.admiralxy.agent.service.provider.AbstractChunkingRagContentProvider;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TextRagContentProvider extends AbstractChunkingRagContentProvider {

    public TextRagContentProvider(TextChunkerService textChunkerService) {
        super(textChunkerService);
    }

    @Override
    public boolean supports(ProviderType providerType) {
        return ProviderType.TEXT == providerType;
    }

    @Override
    protected Mono<String> resolveContent(RagContentRequest request) {
        return Mono.just(request.text());
    }
}
