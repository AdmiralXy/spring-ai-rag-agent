package io.github.admiralxy.agent.service.provider.impl;

import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.service.TextChunkerService;
import io.github.admiralxy.agent.service.provider.RagChunk;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextRagContentProviderTest {

    private final TextRagContentProvider provider = new TextRagContentProvider(noOpChunker());

    @Test
    void supportsTextOnly() {
        assertTrue(provider.supports(ProviderType.TEXT));
        assertFalse(provider.supports(ProviderType.CONFLUENCE));
    }

    @Test
    void resolveChunksReturnsInputText() {
        StepVerifier.create(provider.resolveChunks(new RagContentRequest("raw text", false, null, null)))
                .expectNext(new RagChunk("raw text", 0, 1))
                .verifyComplete();
    }

    private static TextChunkerService noOpChunker() {
        TextChunkerService chunker = Mockito.mock(TextChunkerService.class);
        Mockito.when(chunker.chunk(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(java.util.List.of());
        return chunker;
    }
}
