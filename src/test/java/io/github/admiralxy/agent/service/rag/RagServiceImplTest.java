package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.domain.RagSearchHit;
import io.github.admiralxy.agent.entity.SpaceEntity;
import io.github.admiralxy.agent.repository.RagDocumentRepository;
import io.github.admiralxy.agent.repository.SpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    private static final UUID SPACE_ID = UUID.randomUUID();

    @Mock
    private VectorStore store;

    @Mock
    private RagIndexer indexer;

    @Mock
    private RagDocumentRepository repository;

    @Mock
    private HybridRagRetriever retriever;

    @Mock
    private SpaceRepository spaceRepository;

    private TokenizerService tokenizerService;
    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        tokenizerService = mock(TokenizerService.class);
        ragService = new RagServiceImpl(store, indexer, repository, retriever, new RagContextAssembler(tokenizerService),
                new RagProperties(), tokenizerService, spaceRepository);
    }

    @Test
    void groupsChunksOfOneSourceInOrderAndLabelsThem() {
        SpaceEntity space = new SpaceEntity();
        space.setId(SPACE_ID);
        space.setName("Backend");
        when(spaceRepository.findAllById(any())).thenReturn(List.of(space));
        when(tokenizerService.countTokens(any())).thenAnswer(i -> i.getArgument(0, String.class).length());
        when(retriever.search(any(), any(), any(), anyInt(), anyDouble())).thenReturn(List.of(
                hit("2", "doc-1", "src/A.java", 1, "Source: src/A.java\n\nsecond part"),
                hit("3", "doc-2", "README.md", 0, "Source: README.md\n\nreadme"),
                hit("1", "doc-1", "src/A.java", 0, "Source: src/A.java\n\nfirst part")
        ));

        String result = ragService.buildContext(List.of(SPACE_ID), "question", null, 10_000);

        assertTrue(result.startsWith("<document index=\"1\" source=\"src/A.java\" space=\"Backend\">"));
        assertTrue(result.indexOf("first part") < result.indexOf("second part"));
        assertTrue(result.indexOf("second part") < result.indexOf("readme"));
        assertTrue(result.contains("<document index=\"2\" source=\"README.md\" space=\"Backend\">"));
        assertFalse(result.contains("Source: "));
    }

    @Test
    void keepsWholeChunksWithinTheBudget() {
        when(tokenizerService.countTokens(any())).thenAnswer(i -> i.getArgument(0, String.class).length());
        when(retriever.search(any(), any(), any(), anyInt(), anyDouble())).thenReturn(List.of(
                hit("1", "d1", null, 0, "a".repeat(40)),
                hit("2", "d2", null, 0, "b".repeat(100)),
                hit("3", "d3", null, 0, "c".repeat(30))
        ));

        String result = ragService.buildContext(List.of(SPACE_ID), "q", null, 80);

        assertTrue(result.contains("a".repeat(40)));
        assertFalse(result.contains("bbb"));
        assertTrue(result.contains("c".repeat(30)));
    }

    @Test
    void searchesShortFollowUpsTogetherWithThePreviousQuestion() {
        when(tokenizerService.countTokens(any())).thenReturn(3);
        when(retriever.search(any(), any(), any(), anyInt(), anyDouble())).thenReturn(List.of());

        ragService.buildContext(List.of(SPACE_ID), "and the second?", "How is A configured?", 1000);

        verify(retriever).search(eq(List.of(SPACE_ID.toString())), eq("and the second?"),
                eq("How is A configured?\nand the second?"), anyInt(), anyDouble());
    }

    @Test
    void isEmptyWithoutSpaces() {
        assertEquals("", ragService.buildContext(List.of(), "q", null, 1000));
        verify(retriever, never()).search(any(), any(), any(), anyInt(), anyDouble());
    }

    @Test
    void deletesThroughTheRepository() {
        ragService.deleteSpace("s");
        ragService.deleteDocument("s", "d");
        ragService.deleteChunk("s", "d", "c");

        verify(repository).deleteSpace("s");
        verify(repository).deleteDocument("s", "d");
        verify(repository).deleteChunk("s", "d", "c");
    }

    private static RagSearchHit hit(String id, String doc, String source, int part, String content) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(RagSearchHit.SPACE, SPACE_ID.toString());
        metadata.put(RagSearchHit.DOC, doc);
        if (source != null) {
            metadata.put(RagSearchHit.SOURCE, source);
            metadata.put(RagSearchHit.PART, part);
        }
        return new RagSearchHit(id, content, metadata);
    }
}
