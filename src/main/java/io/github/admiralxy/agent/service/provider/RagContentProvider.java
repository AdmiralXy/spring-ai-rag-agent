package io.github.admiralxy.agent.service.provider;

import io.github.admiralxy.agent.controller.response.documents.ProviderType;

public interface RagContentProvider {

    /**
     * Checks if this provider handles the given provider type.
     *
     * @param providerType provider type
     * @return true if supported
     */
    boolean supports(ProviderType providerType);

    /**
     * Resolves original request text to final content for indexing.
     *
     * @param text source text from request
     * @return resolved content
     */
    String resolveContent(String text);
}
