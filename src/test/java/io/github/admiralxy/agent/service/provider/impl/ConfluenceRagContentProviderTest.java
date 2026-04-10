package io.github.admiralxy.agent.service.provider.impl;

import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfluenceRagContentProviderTest {

    private final ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider();

    @Test
    void supportsConfluenceOnly() {
        assertTrue(provider.supports(ProviderType.CONFLUENCE));
        assertFalse(provider.supports(ProviderType.TEXT));
    }

    @Test
    void resolveContentReturnsPlaceholder() {
        assertEquals("Hello world!", provider.resolveContent("ignored"));
    }
}
