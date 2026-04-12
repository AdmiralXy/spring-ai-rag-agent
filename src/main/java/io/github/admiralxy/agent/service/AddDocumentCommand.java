package io.github.admiralxy.agent.service;

import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.service.provider.RagContentRequest;

public record AddDocumentCommand(
        String spaceId,
        ProviderType providerType,
        RagContentRequest contentRequest
) {
}
