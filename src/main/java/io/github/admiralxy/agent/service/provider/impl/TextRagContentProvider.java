package io.github.admiralxy.agent.service.provider.impl;

import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.service.provider.RagContentProvider;
import org.springframework.stereotype.Component;

@Component
public class TextRagContentProvider implements RagContentProvider {

    @Override
    public boolean supports(ProviderType providerType) {
        return ProviderType.TEXT == providerType;
    }

    @Override
    public String resolveContent(String text) {
        return text;
    }
}
