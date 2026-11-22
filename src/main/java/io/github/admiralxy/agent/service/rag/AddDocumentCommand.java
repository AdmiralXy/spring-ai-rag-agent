package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.ProviderType;
import io.github.admiralxy.agent.service.provider.RagContentRequest;

public record AddDocumentCommand(
        String spaceId,
        ProviderType providerType,
        RagContentRequest contentRequest
) {
}
