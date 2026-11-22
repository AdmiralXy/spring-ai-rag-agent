package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.ProviderType;
import io.github.admiralxy.agent.domain.RagSearchHit;
import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import io.github.admiralxy.agent.entity.EmbeddingModelProvider;
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
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagIndexerTest {

    private static final String SPACE_ID = "space-1";
    private static final String TOKEN_LIMIT_ERROR =
            "Tokens in a single document exceeds the maximum number of allowed input tokens";

    @Mock
    private VectorStore store;

    @Mock
    private ModelSettingsService modelSettingsService;

    @Mock
    private RagContentProvider textProvider;

    @Mock
    private RagContentProvider confluenceProvider;

    private TokenizerService tokenizerService;
    private EmbeddingModelEntity embeddingModel;
    private RagIndexer indexer;

    @BeforeEach
    void setUp() {
        tokenizerService = mock(TokenizerService.class);
        embeddingModel = new EmbeddingModelEntity();
        embeddingModel.setProvider(EmbeddingModelProvider.OPENAI);
        embeddingModel.setMaxDocumentTokens(8000);
        lenient().when(modelSettingsService.getEmbeddingModel()).thenReturn(embeddingModel);
        lenient().when(tokenizerService.splitToTokenChunks(any(), anyInt()))
                .thenAnswer(invocation -> List.of(invocation.getArgument(0, String.class)));
        indexer = new RagIndexer(store, tokenizerService, modelSettingsService,
                List.of(textProvider, confluenceProvider));
    }

    @Test
    void storesTextChunkAsIs() {
        when(textProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("raw text", 0, 1)));

        StepVerifier.create(indexer.index(command(ProviderType.TEXT))).expectNext(100).verifyComplete();

        verify(store).add(argThat(docs -> docs.size() == 1 && "raw text".equals(docs.getFirst().getText())));
    }

    @Test
    void usesTextProviderWhenTypeIsMissing() {
        when(textProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("fallback", 0, 1)));

        StepVerifier.create(indexer.index(command(null))).expectNext(100).verifyComplete();

        verify(textProvider).resolveChunks(any());
    }

    @Test
    void picksTheMatchingProvider() {
        when(textProvider.supports(ProviderType.CONFLUENCE)).thenReturn(false);
        when(confluenceProvider.supports(ProviderType.CONFLUENCE)).thenReturn(true);
        when(confluenceProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("Hello", 0, 1)));

        StepVerifier.create(indexer.index(command(ProviderType.CONFLUENCE))).expectNext(100).verifyComplete();

        verify(confluenceProvider).resolveChunks(any());
    }

    @Test
    void reportsProgressPerChunk() {
        when(textProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textProvider.resolveChunks(any())).thenReturn(Flux.just(
                new RagChunk("chunk 1", 0, 2),
                new RagChunk("chunk 2", 1, 2)
        ));

        StepVerifier.create(indexer.index(command(ProviderType.TEXT))).expectNext(50).expectNext(100).verifyComplete();

        verify(store, times(2)).add(anyList());
    }

    @Test
    void prefixesChunksOfKnownSourceAndStoresItsPosition() {
        when(textProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textProvider.resolveChunks(any()))
                .thenReturn(Flux.just(new RagChunk("body", 0, 1, "src/A.java", 2, 5)));

        StepVerifier.create(indexer.index(command(ProviderType.TEXT))).expectNext(100).verifyComplete();

        verify(store).add(argThat(docs -> {
            Document document = docs.getFirst();
            return "Source: src/A.java\n\nbody".equals(document.getText())
                    && "src/A.java".equals(document.getMetadata().get(RagSearchHit.SOURCE))
                    && Integer.valueOf(2).equals(document.getMetadata().get(RagSearchHit.PART))
                    && SPACE_ID.equals(document.getMetadata().get(RagSearchHit.SPACE));
        }));
    }

    @Test
    void storesOversizedChunkInOneBatch() {
        when(textProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("big chunk", 0, 1)));
        when(tokenizerService.splitToTokenChunks("big chunk", 2)).thenReturn(List.of("part-1", "part-2"));
        embeddingModel.setMaxDocumentTokens(2);

        StepVerifier.create(indexer.index(command(ProviderType.TEXT))).expectNext(100).verifyComplete();

        verify(store).add(argThat(docs -> docs.size() == 2));
    }

    @Test
    void splitsChunkFurtherWhenTheEmbeddingModelRejectsIt() {
        when(textProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("too-big", 0, 1)));
        when(tokenizerService.countTokens("too-big")).thenReturn(10);
        when(tokenizerService.splitToTokenChunks("too-big", 8000)).thenReturn(List.of("too-big"));
        when(tokenizerService.splitToTokenChunks("too-big", 5)).thenReturn(List.of("small-1", "small-2"));
        AtomicInteger calls = new AtomicInteger();
        doAnswer(ignored -> {
            if (calls.incrementAndGet() == 1) {
                throw new RuntimeException(TOKEN_LIMIT_ERROR);
            }
            return null;
        }).when(store).add(anyList());

        StepVerifier.create(indexer.index(command(ProviderType.TEXT))).expectNext(100).verifyComplete();

        verify(store, times(3)).add(anyList());
    }

    private static AddDocumentCommand command(ProviderType type) {
        return new AddDocumentCommand(SPACE_ID, type, new RagContentRequest("x", false, null, null));
    }
}
