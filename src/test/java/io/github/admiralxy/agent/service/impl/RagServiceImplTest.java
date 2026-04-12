package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.entity.EmbeddingModelProviderType;
import io.github.admiralxy.agent.entity.EmbeddingsModelSettingsEntity;
import io.github.admiralxy.agent.service.AddDocumentCommand;
import io.github.admiralxy.agent.service.TokenizerService;
import io.github.admiralxy.agent.service.model.ModelSettingsService;
import io.github.admiralxy.agent.service.provider.RagChunk;
import io.github.admiralxy.agent.service.provider.RagContentProvider;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    private static final String SPACE_ID = "space-1";
    private static final String SPACE_ID_2 = "space-2";

    @Mock
    private VectorStore store;

    private TokenizerService tokenizerService;
    private EmbeddingsModelSettingsEntity embeddingsModel;

    @Mock
    private ModelSettingsService modelSettingsService;

    @Mock
    private RagContentProvider textContentProvider;

    @Mock
    private RagContentProvider confluenceContentProvider;

    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        tokenizerService = org.mockito.Mockito.mock(TokenizerService.class);
        embeddingsModel = new EmbeddingsModelSettingsEntity();
        embeddingsModel.setProvider(EmbeddingModelProviderType.OPENAI);
        embeddingsModel.setMaxDocumentTokens(8000);
        lenient().when(modelSettingsService.getEmbeddingsModel()).thenReturn(embeddingsModel);
        lenient().when(tokenizerService.splitToTokenChunks(any(), anyInt()))
                .thenAnswer(invocation -> List.of(invocation.getArgument(0, String.class)));
        ragService = new RagServiceImpl(
                store,
                tokenizerService,
                modelSettingsService,
                List.of(textContentProvider, confluenceContentProvider)
        );
    }

    @Test
    void addUsesTextProviderContentAsIs() {
        // GIVEN
        when(textContentProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textContentProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("raw text", 0, 1)));

        // WHEN
        Flux<Integer> result = ragService.add(command(ProviderType.TEXT, "raw text", false));

        // THEN
        StepVerifier.create(result)
                .expectNext(100)
                .verifyComplete();

        verify(textContentProvider).supports(ProviderType.TEXT);
        verify(textContentProvider).resolveChunks(any());
        verify(store).add(argThat(docs -> docs.size() == 1 && "raw text".equals(docs.getFirst().getText())));
    }

    @Test
    void addUsesConfluenceProviderPlaceholderContent() {
        // GIVEN
        when(textContentProvider.supports(ProviderType.CONFLUENCE)).thenReturn(false);
        when(confluenceContentProvider.supports(ProviderType.CONFLUENCE)).thenReturn(true);
        when(confluenceContentProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("Hello world!", 0, 1)));

        // WHEN
        Flux<Integer> result = ragService.add(command(ProviderType.CONFLUENCE, "ignored", false));

        // THEN
        StepVerifier.create(result)
                .expectNext(100)
                .verifyComplete();

        verify(confluenceContentProvider).supports(ProviderType.CONFLUENCE);
        verify(confluenceContentProvider).resolveChunks(any());
        verify(store).add(argThat(docs -> docs.size() == 1 && "Hello world!".equals(docs.getFirst().getText())));
    }

    @Test
    void addStoresAllProviderChunks() {
        // GIVEN
        when(textContentProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textContentProvider.resolveChunks(any())).thenReturn(Flux.just(
                new RagChunk("chunk 1", 0, 2),
                new RagChunk("chunk 2", 1, 2)
        ));

        // WHEN
        Flux<Integer> result = ragService.add(command(ProviderType.TEXT, "batch text", true));

        // THEN
        StepVerifier.create(result)
                .expectNext(50)
                .expectNext(100)
                .verifyComplete();

        verify(store, times(2)).add(anyList());
    }

    @Test
    void addUsesTextProviderAsDefaultWhenProviderTypeIsNull() {
        // GIVEN
        when(textContentProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textContentProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("fallback text", 0, 1)));

        // WHEN
        Flux<Integer> result = ragService.add(command(null, "fallback text", false));

        // THEN
        StepVerifier.create(result)
                .expectNext(100)
                .verifyComplete();

        verify(textContentProvider).supports(ProviderType.TEXT);
        verify(textContentProvider).resolveChunks(any());
        verify(store).add(argThat(docs -> docs.size() == 1 && "fallback text".equals(docs.getFirst().getText())));
    }

    @Test
    void buildContextUsesTokenizerService() {
        // GIVEN
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(
                List.of(new Document("d1", "alpha",
                Map.of("space", SPACE_ID, "doc", "d1", "chunk", "c1")))
        );
        when(tokenizerService.countTokens(any())).thenReturn(1);

        // WHEN
        String result = ragService.buildContext(List.of(SPACE_ID), "q", 100.0, 100, 1);

        // THEN
        assertTrue(result.contains("[Source: space=space-1, doc=d1, chunk=c1]"));
        assertTrue(result.contains("alpha"));
    }

    @Test
    void buildContextKeepsTopOrderAndAddsSourceHeaders() {
        // GIVEN
        List<Document> docs = List.of(
                new Document("a1", "a1", Map.of("space", SPACE_ID, "doc", "da1", "chunk", "ca1")),
                new Document("a2", "a2", Map.of("space", SPACE_ID, "doc", "da2", "chunk", "ca2")),
                new Document("b1", "b1", Map.of("space", SPACE_ID_2, "doc", "db1", "chunk", "cb1")),
                new Document("a3", "a3", Map.of("space", SPACE_ID, "doc", "da3", "chunk", "ca3")),
                new Document("b2", "b2", Map.of("space", SPACE_ID_2, "doc", "db2", "chunk", "cb2"))
        );
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(docs);
        when(tokenizerService.countTokens(any())).thenReturn(1);

        // WHEN
        String result = ragService.buildContext(List.of(SPACE_ID, SPACE_ID_2), "q", 100.0, 100, 3);

        // THEN
        assertTrue(result.indexOf("a1") < result.indexOf("a2"));
        assertTrue(result.indexOf("a2") < result.indexOf("b1"));
        assertTrue(result.contains("[Source: space=space-1, doc=da1, chunk=ca1]"));
        assertTrue(result.contains("[Source: space=space-2, doc=db1, chunk=cb1]"));
    }

    @Test
    void buildContextDoesNotUseDeepRebalanceCandidates() {
        // GIVEN
        List<Document> docs = List.of(
                new Document("a1", "A1", Map.of("space", SPACE_ID, "doc", "da1", "chunk", "ca1")),
                new Document("a2", "A2", Map.of("space", SPACE_ID, "doc", "da2", "chunk", "ca2")),
                new Document("a3", "A3", Map.of("space", SPACE_ID, "doc", "da3", "chunk", "ca3")),
                new Document("a4", "A4", Map.of("space", SPACE_ID, "doc", "da4", "chunk", "ca4")),
                new Document("a5", "A5", Map.of("space", SPACE_ID, "doc", "da5", "chunk", "ca5")),
                new Document("a6", "A6", Map.of("space", SPACE_ID, "doc", "da6", "chunk", "ca6")),
                new Document("b1", "B-CONTENT", Map.of("space", SPACE_ID_2, "doc", "db1", "chunk", "cb1"))
        );
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(docs);
        when(tokenizerService.countTokens(any())).thenReturn(1);

        // WHEN
        String result = ragService.buildContext(List.of(SPACE_ID, SPACE_ID_2), "q", 100.0, 100, 3);

        // THEN
        assertFalse(result.contains("B-CONTENT"));
        assertTrue(result.contains("A1"));
        assertTrue(result.contains("A2"));
        assertTrue(result.contains("A3"));
    }

    @Test
    void addSplitsChunkWhenTokenLimitExceeded() {
        // GIVEN
        when(textContentProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textContentProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("big chunk", 0, 1)));
        when(tokenizerService.splitToTokenChunks("big chunk", 2)).thenReturn(List.of("part-1", "part-2"));
        embeddingsModel.setMaxDocumentTokens(2);

        // WHEN
        Flux<Integer> result = ragService.add(command(ProviderType.TEXT, "x", false));

        // THEN
        StepVerifier.create(result)
                .expectNext(100)
                .verifyComplete();
        verify(store, times(2)).add(anyList());
    }

    @Test
    void addRetriesWithSmallerChunksWhenStoreReturnsTokenLimitError() {
        // GIVEN
        when(textContentProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textContentProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("too-big", 0, 1)));
        when(tokenizerService.countTokens("too-big")).thenReturn(10);
        when(tokenizerService.splitToTokenChunks("too-big", 8000)).thenReturn(List.of("too-big"));
        when(tokenizerService.splitToTokenChunks("too-big", 5)).thenReturn(List.of("small-1", "small-2"));

        AtomicInteger call = new AtomicInteger();
        doAnswer(ignored -> {
            int current = call.incrementAndGet();
            if (current == 1) {
                throw new RuntimeException("Tokens in a single document exceeds the maximum number of allowed input tokens");
            }
            return null;
        }).when(store).add(anyList());

        // WHEN
        Flux<Integer> result = ragService.add(command(ProviderType.TEXT, "x", false));

        // THEN
        StepVerifier.create(result)
                .expectNext(100)
                .verifyComplete();
        verify(store, times(3)).add(anyList());
    }

    private AddDocumentCommand command(ProviderType providerType, String text, boolean batch) {
        return new AddDocumentCommand(SPACE_ID, providerType, new RagContentRequest(text, batch, null, null));
    }
}
