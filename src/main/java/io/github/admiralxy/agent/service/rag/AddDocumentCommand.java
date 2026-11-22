package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.ProviderType;
import io.github.admiralxy.agent.service.provider.RagContentRequest;

import java.util.UUID;

public record AddDocumentCommand(
        UUID userId,
        UUID spaceId,
        ProviderType providerType,
        RagContentRequest contentRequest
) {
}
