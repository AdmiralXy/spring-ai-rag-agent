package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.ProviderType;
import io.github.admiralxy.agent.domain.RagSearchHit;
import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import io.github.admiralxy.agent.entity.EmbeddingModelProvider;
import io.github.admiralxy.agent.exception.ApiException;
import io.github.admiralxy.agent.repository.RagDocumentRepository;
import io.github.admiralxy.agent.repository.RagDocumentRepository.NewChunk;
import io.github.admiralxy.agent.service.model.EmbeddingModelRuntime;
import io.github.admiralxy.agent.service.provider.RagChunk;
import io.github.admiralxy.agent.service.provider.RagContentProvider;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagIndexerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final int DIMENSIONS = 3;
    private static final String TOKEN_LIMIT_ERROR =
            "Tokens in a single document exceeds the maximum number of allowed input tokens";

    @Mock
    private RagDocumentRepository repository;

    @Mock
    private EmbeddingModelRuntime embeddingModelRuntime;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private RagContentProvider textProvider;

    @Mock
    private RagContentProvider confluenceProvider;

    private TokenizerService tokenizerService;
    private EmbeddingModelEntity settings;
    private RagIndexer indexer;

    @BeforeEach
    void setUp() {
        tokenizerService = mock(TokenizerService.class);
        settings = new EmbeddingModelEntity();
        settings.setUserId(USER_ID);
        settings.setProvider(EmbeddingModelProvider.OPENAI);
        settings.setName("text-embedding-3-small");
        settings.setBaseUrl("https://api.openai.com");
        settings.setDimensions(DIMENSIONS);
        settings.setMaxDocumentTokens(8000);
        lenient().when(embeddingModelRuntime.forUser(USER_ID))
                .thenReturn(new EmbeddingModelRuntime.Embedder(settings, embeddingModel));
        lenient().when(embeddingModel.embed(anyList()))
                .thenAnswer(invocation -> vectors(invocation.getArgument(0, List.class).size()));
        lenient().when(tokenizerService.splitToTokenChunks(any(), anyInt()))
                .thenAnswer(invocation -> List.of(invocation.getArgument(0, String.class)));
        indexer = new RagIndexer(repository, tokenizerService, embeddingModelRuntime,
                List.of(textProvider, confluenceProvider));
    }

    @Test
    void storesTextChunkAsIs() {
        when(textProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("raw text", 0, 1)));

        StepVerifier.create(indexer.index(command(ProviderType.TEXT))).expectNext(100).verifyComplete();

        verify(repository).insert(eq(SPACE_ID), eq(DIMENSIONS),
                argThat(rows -> rows.size() == 1 && "raw text".equals(rows.getFirst().content())));
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

        verify(repository, times(2)).insert(eq(SPACE_ID), eq(DIMENSIONS), anyList());
    }

    @Test
    void prefixesChunksOfKnownSourceAndStoresItsPosition() {
        when(textProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textProvider.resolveChunks(any()))
                .thenReturn(Flux.just(new RagChunk("body", 0, 1, "src/A.java", 2, 5)));

        StepVerifier.create(indexer.index(command(ProviderType.TEXT))).expectNext(100).verifyComplete();

        verify(repository).insert(eq(SPACE_ID), eq(DIMENSIONS), argThat(rows -> {
            NewChunk row = rows.getFirst();
            return "Source: src/A.java\n\nbody".equals(row.content())
                    && "src/A.java".equals(row.metadata().get(RagSearchHit.SOURCE))
                    && Integer.valueOf(2).equals(row.metadata().get(RagSearchHit.PART))
                    && SPACE_ID.toString().equals(row.metadata().get(RagSearchHit.SPACE))
                    && row.id().toString().equals(row.metadata().get(RagSearchHit.CHUNK));
        }));
    }

    @Test
    void storesOversizedChunkInOneBatch() {
        when(textProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("big chunk", 0, 1)));
        when(tokenizerService.splitToTokenChunks("big chunk", 2)).thenReturn(List.of("part-1", "part-2"));
        settings.setMaxDocumentTokens(2);

        StepVerifier.create(indexer.index(command(ProviderType.TEXT))).expectNext(100).verifyComplete();

        verify(repository).insert(eq(SPACE_ID), eq(DIMENSIONS), argThat(rows -> rows.size() == 2));
    }

    @Test
    void splitsChunkFurtherWhenTheEmbeddingModelRejectsIt() {
        when(textProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("too-big", 0, 1)));
        when(tokenizerService.countTokens("too-big")).thenReturn(10);
        when(tokenizerService.splitToTokenChunks("too-big", 8000)).thenReturn(List.of("too-big"));
        when(tokenizerService.splitToTokenChunks("too-big", 5)).thenReturn(List.of("small-1", "small-2"));
        AtomicInteger calls = new AtomicInteger();
        when(embeddingModel.embed(anyList())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                throw new RuntimeException(TOKEN_LIMIT_ERROR);
            }
            return vectors(invocation.getArgument(0, List.class).size());
        });

        StepVerifier.create(indexer.index(command(ProviderType.TEXT))).expectNext(100).verifyComplete();

        verify(repository, times(2)).insert(eq(SPACE_ID), eq(DIMENSIONS), anyList());
    }

    @Test
    void explainsAnUnreachableEmbeddingModel() {
        when(textProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("text", 0, 1)));
        when(embeddingModel.embed(anyList()))
                .thenThrow(new IllegalStateException("I/O", new ConnectException("Connection refused")));

        StepVerifier.create(indexer.index(command(ProviderType.TEXT)))
                .expectErrorMatches(e -> e instanceof ApiException
                        && e.getMessage().contains("text-embedding-3-small")
                        && e.getMessage().contains("api.openai.com")
                        && e.getMessage().contains("refuses connections"))
                .verify();
        verify(repository, never()).insert(any(), anyInt(), anyList());
    }

    @Test
    void rejectsVectorsOfAnotherDimension() {
        when(textProvider.supports(ProviderType.TEXT)).thenReturn(true);
        when(textProvider.resolveChunks(any())).thenReturn(Flux.just(new RagChunk("text", 0, 1)));
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[DIMENSIONS + 1]));

        StepVerifier.create(indexer.index(command(ProviderType.TEXT)))
                .expectErrorMatches(e -> e instanceof ApiException && e.getMessage().contains("Set Dimensions to 4"))
                .verify();
    }

    private static List<float[]> vectors(int count) {
        return Collections.nCopies(count, new float[DIMENSIONS]);
    }

    private static AddDocumentCommand command(ProviderType type) {
        return new AddDocumentCommand(USER_ID, SPACE_ID, type, new RagContentRequest("x", false, null, null));
    }
}
