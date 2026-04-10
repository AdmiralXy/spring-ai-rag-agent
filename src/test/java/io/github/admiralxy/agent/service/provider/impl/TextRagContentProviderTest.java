package io.github.admiralxy.agent.service.provider.impl;

import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextRagContentProviderTest {

    private final TextRagContentProvider provider = new TextRagContentProvider();

    @Test
    void supportsTextOnly() {
        assertTrue(provider.supports(ProviderType.TEXT));
        assertFalse(provider.supports(ProviderType.CONFLUENCE));
    }

    @Test
    void resolveContentReturnsInputText() {
        assertEquals("raw text", provider.resolveContent("raw text"));
    }
}
