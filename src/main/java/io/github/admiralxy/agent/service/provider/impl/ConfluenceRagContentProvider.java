package io.github.admiralxy.agent.service.provider.impl;

import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.service.provider.RagContentProvider;
import org.springframework.stereotype.Component;

@Component
public class ConfluenceRagContentProvider implements RagContentProvider {

    private static final String PLACEHOLDER_CONTENT = "Hello world!";

    @Override
    public boolean supports(ProviderType providerType) {
        return ProviderType.CONFLUENCE == providerType;
    }

    @Override
    public String resolveContent(String text) {
        return PLACEHOLDER_CONTENT;
    }
}
