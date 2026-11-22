package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.domain.RagSearchHit;
import io.github.admiralxy.agent.entity.SpaceEntity;
import io.github.admiralxy.agent.exception.ApiException;
import io.github.admiralxy.agent.repository.RagDocumentRepository;
import io.github.admiralxy.agent.repository.SpaceRepository;
import io.github.admiralxy.agent.service.model.EmbeddingModelRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();

    @Mock
    private RagIndexer indexer;

    @Mock
    private RagDocumentRepository repository;

    @Mock
    private HybridRagRetriever retriever;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private EmbeddingModelRuntime embeddingModelRuntime;

    private TokenizerService tokenizerService;
    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        tokenizerService = mock(TokenizerService.class);
        ragService = new RagServiceImpl(indexer, repository, retriever, new RagContextAssembler(tokenizerService),
                new RagProperties(), tokenizerService, spaceRepository, embeddingModelRuntime);
        SpaceEntity space = new SpaceEntity();
        space.setId(SPACE_ID);
        space.setUserId(USER_ID);
        space.setName("Backend");
        lenient().when(spaceRepository.findByUserIdAndIdIn(eq(USER_ID), any())).thenReturn(List.of(space));
    }

    @Test
    void groupsChunksOfOneSourceInOrderAndLabelsThem() {
        when(tokenizerService.countTokens(any())).thenAnswer(i -> i.getArgument(0, String.class).length());
        when(retriever.search(any(), any(), any(), any(), anyInt(), anyDouble())).thenReturn(List.of(
                hit("2", "doc-1", "src/A.java", 1, "Source: src/A.java\n\nsecond part"),
                hit("3", "doc-2", "README.md", 0, "Source: README.md\n\nreadme"),
                hit("1", "doc-1", "src/A.java", 0, "Source: src/A.java\n\nfirst part")
        ));

        String result = ragService.buildContext(USER_ID, List.of(SPACE_ID), "question", null, 10_000);

        assertTrue(result.startsWith("<document index=\"1\" source=\"src/A.java\" space=\"Backend\">"));
        assertTrue(result.indexOf("first part") < result.indexOf("second part"));
        assertTrue(result.indexOf("second part") < result.indexOf("readme"));
        assertTrue(result.contains("<document index=\"2\" source=\"README.md\" space=\"Backend\">"));
        assertFalse(result.contains("Source: "));
    }

    @Test
    void keepsWholeChunksWithinTheBudget() {
        when(tokenizerService.countTokens(any())).thenAnswer(i -> i.getArgument(0, String.class).length());
        when(retriever.search(any(), any(), any(), any(), anyInt(), anyDouble())).thenReturn(List.of(
                hit("1", "d1", null, 0, "a".repeat(40)),
                hit("2", "d2", null, 0, "b".repeat(100)),
                hit("3", "d3", null, 0, "c".repeat(30))
        ));

        String result = ragService.buildContext(USER_ID, List.of(SPACE_ID), "q", null, 80);

        assertTrue(result.contains("a".repeat(40)));
        assertFalse(result.contains("bbb"));
        assertTrue(result.contains("c".repeat(30)));
    }

    @Test
    void searchesShortFollowUpsTogetherWithThePreviousQuestion() {
        when(tokenizerService.countTokens(any())).thenReturn(3);
        when(retriever.search(any(), any(), any(), any(), anyInt(), anyDouble())).thenReturn(List.of());

        ragService.buildContext(USER_ID, List.of(SPACE_ID), "and the second?", "How is A configured?", 1000);

        verify(retriever).search(any(), eq(List.of(SPACE_ID)), eq("and the second?"),
                eq("How is A configured?\nand the second?"), anyInt(), anyDouble());
    }

    @Test
    void ignoresSpacesOfOtherUsers() {
        UUID stranger = UUID.randomUUID();
        when(spaceRepository.findByUserIdAndIdIn(eq(stranger), any())).thenReturn(List.of());

        assertEquals("", ragService.buildContext(stranger, List.of(SPACE_ID), "q", null, 1000));
        verify(retriever, never()).search(any(), any(), anyString(), any(), anyInt(), anyDouble());
    }

    @Test
    void isEmptyWithoutSpaces() {
        assertEquals("", ragService.buildContext(USER_ID, List.of(), "q", null, 1000));
        verify(retriever, never()).search(any(), any(), any(), any(), anyInt(), anyDouble());
    }

    @Test
    void deletesThroughTheRepositoryOnlyInOwnSpaces() {
        when(spaceRepository.existsByIdAndUserId(SPACE_ID, USER_ID)).thenReturn(true);

        ragService.deleteDocument(USER_ID, SPACE_ID, "d");
        ragService.deleteChunk(USER_ID, SPACE_ID, "d", "c");

        verify(repository).deleteDocument(SPACE_ID, "d");
        verify(repository).deleteChunk(SPACE_ID, "d", "c");
        assertThrows(ApiException.class, () -> ragService.deleteDocument(UUID.randomUUID(), SPACE_ID, "d"));
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
