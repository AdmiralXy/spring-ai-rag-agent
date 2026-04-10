package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.service.TextChunkerService;
import io.github.admiralxy.agent.service.TokenizerService;
import io.github.admiralxy.agent.service.provider.RagContentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    private static final String SPACE_ID = "space-1";

    @Mock
    private VectorStore store;

    @Mock
    private TextChunkerService textChunkerService;

    private TokenizerService tokenizerService;

    @Mock
    private RagContentProvider textContentProvider;

    @Mock
    private RagContentProvider confluenceContentProvider;

    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        tokenizerService = org.mockito.Mockito.mock(TokenizerService.class);
        ragService = new RagServiceImpl(
                store,
                textChunkerService,
                tokenizerService,
                List.of(textContentProvider, confluenceContentProvider)
        );
    }

    @Test
    void addUsesTextProviderContentAsIs() {
        // GIVEN
        when(textContentProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textContentProvider.resolveContent("raw text")).thenReturn("raw text");

        // WHEN
        Flux<Integer> result = ragService.add(SPACE_ID, "raw text", false, ProviderType.TEXT);

        // THEN
        StepVerifier.create(result)
                .expectNext(100)
                .verifyComplete();

        verify(textContentProvider).supports(ProviderType.TEXT);
        verify(textContentProvider).resolveContent("raw text");
        verify(store).add(argThat(docs -> docs.size() == 1 && "raw text".equals(docs.getFirst().getText())));
        verify(textChunkerService, never()).chunk(eq("raw text"), eq(100), eq(1500), eq(50));
    }

    @Test
    void addUsesConfluenceProviderPlaceholderContent() {
        // GIVEN
        when(textContentProvider.supports(ProviderType.CONFLUENCE)).thenReturn(false);
        when(confluenceContentProvider.supports(ProviderType.CONFLUENCE)).thenReturn(true);
        when(confluenceContentProvider.resolveContent("ignored")).thenReturn("Hello world!");

        // WHEN
        Flux<Integer> result = ragService.add(SPACE_ID, "ignored", false, ProviderType.CONFLUENCE);

        // THEN
        StepVerifier.create(result)
                .expectNext(100)
                .verifyComplete();

        verify(confluenceContentProvider).supports(ProviderType.CONFLUENCE);
        verify(confluenceContentProvider).resolveContent("ignored");
        verify(store).add(argThat(docs -> docs.size() == 1 && "Hello world!".equals(docs.getFirst().getText())));
    }

    @Test
    void addUsesChunkerForBatchTextProvider() {
        // GIVEN
        when(textContentProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textContentProvider.resolveContent("batch text")).thenReturn("batch text");
        when(textChunkerService.chunk("batch text", 100, 1500, 50)).thenReturn(List.of("chunk 1", "chunk 2"));

        // WHEN
        Flux<Integer> result = ragService.add(SPACE_ID, "batch text", true, ProviderType.TEXT);

        // THEN
        StepVerifier.create(result)
                .expectNext(50)
                .expectNext(100)
                .verifyComplete();

        verify(textChunkerService).chunk("batch text", 100, 1500, 50);
        verify(store, times(2)).add(anyList());
    }

    @Test
    void addUsesTextProviderAsDefaultWhenProviderTypeIsNull() {
        // GIVEN
        when(textContentProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textContentProvider.resolveContent("fallback text")).thenReturn("fallback text");

        // WHEN
        Flux<Integer> result = ragService.add(SPACE_ID, "fallback text", false, null);

        // THEN
        StepVerifier.create(result)
                .expectNext(100)
                .verifyComplete();

        verify(textContentProvider).supports(ProviderType.TEXT);
        verify(textContentProvider).resolveContent("fallback text");
        verify(store).add(argThat(docs -> docs.size() == 1 && "fallback text".equals(docs.getFirst().getText())));
    }

    @Test
    void buildContextUsesTokenizerService() {
        // GIVEN
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document("d1", "alpha", Map.of())));
        when(tokenizerService.countTokens("alpha")).thenReturn(1);

        // WHEN
        String result = ragService.buildContext(SPACE_ID, "q", 100.0, 100, 1);

        // THEN
        assertEquals("alpha\n---\n", result);
        verify(tokenizerService, times(2)).countTokens("alpha");
    }
}
