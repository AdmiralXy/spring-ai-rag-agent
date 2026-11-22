package io.github.admiralxy.agent.controller.request.documents;

import io.github.admiralxy.agent.domain.ProviderType;

public record AddToSpaceRq(
        String text,
        boolean batch,
        ProviderType providerType,
        GitProviderRq git,
        ProviderAuthRq auth
) {
}
