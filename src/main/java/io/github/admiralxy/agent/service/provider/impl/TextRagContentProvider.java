package io.github.admiralxy.agent.service.provider.impl;

import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.service.provider.RagContentProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TextRagContentProvider implements RagContentProvider {

    @Override
    public boolean supports(ProviderType providerType) {
        return ProviderType.TEXT == providerType;
    }

    @Override
    public Mono<String> resolveContent(String text) {
        return Mono.just(text);
    }
}
