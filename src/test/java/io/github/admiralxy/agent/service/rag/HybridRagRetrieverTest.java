package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.RagSearchHit;
import io.github.admiralxy.agent.repository.RagDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridRagRetrieverTest {

    private final RagDocumentRepository repository = mock(RagDocumentRepository.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final HybridRagRetriever retriever = new HybridRagRetriever(repository, embeddingModel);

    @Test
    void tsQueryKeepsMeaningfulWordsAsPrefixOrQuery() {
        assertEquals("getuserbyid:* | work:* | users:* | v2:*",
                HybridRagRetriever.toTsQuery("How does getUserById work with users in v2?"));
    }

    @Test
    void tsQueryDropsSyntaxAndStopWords() {
        assertEquals("ошибка:* | npe:* | servicea:*",
                HybridRagRetriever.toTsQuery("Почему ошибка NPE в ServiceA? ' | & ! :*"));
        assertEquals("", HybridRagRetriever.toTsQuery("как это?"));
    }

    @Test
    void fusesVectorAndKeywordRankings() {
        RagSearchHit a = hit("a");
        RagSearchHit b = hit("b");
        RagSearchHit c = hit("c");
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f});
        when(repository.vectorSearch(any(), any(), anyInt())).thenReturn(List.of(a, b));
        when(repository.keywordSearch(any(), eq("retrypolicy:*"), anyInt())).thenReturn(List.of(c, b));

        List<RagSearchHit> ranked = retriever.search(List.of("s"), "RetryPolicy", null, 10, 0.6);

        assertEquals(List.of("b", "a", "c"), ranked.stream().map(RagSearchHit::id).toList());
    }

    private static RagSearchHit hit(String id) {
        return new RagSearchHit(id, id, Map.of());
    }
}
