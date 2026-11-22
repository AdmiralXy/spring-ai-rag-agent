package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.domain.RagSearchHit;
import io.github.admiralxy.agent.repository.RagDocumentRepository;
import io.github.admiralxy.agent.repository.SpaceRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private static final String SPACE_FILTER = "space == '%s'";
    private static final int FOLLOW_UP_MAX_TOKENS = 24;
    private static final String SEARCH_FAILED = "Knowledge base search failed, check the embedding model settings: %s";

    private final VectorStore store;
    private final RagIndexer indexer;
    private final RagDocumentRepository repository;
    private final HybridRagRetriever retriever;
    private final RagContextAssembler assembler;
    private final RagProperties ragProperties;
    private final TokenizerService tokenizerService;
    private final SpaceRepository spaceRepository;

    @Override
    public Flux<Integer> add(AddDocumentCommand command) {
        return indexer.index(command);
    }

    @Override
    public List<Document> search(String spaceId, String query, int topK) {
        return store.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(SPACE_FILTER.formatted(spaceId))
                .build());
    }

    @Override
    public List<RagSearchHit> listDocuments(String spaceId, int limit) {
        return repository.list(spaceId, limit);
    }

    @Override
    public void deleteSpace(String spaceId) {
        repository.deleteSpace(spaceId);
    }

    @Override
    public void deleteDocument(String spaceId, String docId) {
        repository.deleteDocument(spaceId, docId);
    }

    @Override
    public void deleteChunk(String spaceId, String docId, String chunkId) {
        repository.deleteChunk(spaceId, docId, chunkId);
    }

    @Override
    public String buildContext(Collection<UUID> spaceIds, String query, @Nullable String previousQuery,
                               int maxTokens) {
        if (spaceIds == null || spaceIds.isEmpty() || maxTokens <= 0 || StringUtils.isBlank(query)) {
            return StringUtils.EMPTY;
        }
        List<String> spaces = spaceIds.stream().map(UUID::toString).distinct().toList();
        List<RagSearchHit> ranked;
        try {
            ranked = retriever.search(spaces, query, contextualQuery(query, previousQuery),
                    ragProperties.getTopK(), ragProperties.getKeywordWeight());
        } catch (RuntimeException e) {
            throw new IllegalStateException(SEARCH_FAILED.formatted(e.getMessage()), e);
        }
        if (ranked.isEmpty()) {
            return StringUtils.EMPTY;
        }
        return assembler.assemble(ranked, retriever::neighbours, spaceNames(spaceIds), maxTokens,
                ragProperties.getExpandTopHits());
    }

    @Nullable
    private String contextualQuery(String query, @Nullable String previousQuery) {
        if (StringUtils.isBlank(previousQuery) || tokenizerService.countTokens(query) > FOLLOW_UP_MAX_TOKENS) {
            return null;
        }
        return previousQuery.strip() + StringUtils.LF + query.strip();
    }

    private Map<String, String> spaceNames(Collection<UUID> spaceIds) {
        Map<String, String> names = new HashMap<>();
        spaceRepository.findAllById(spaceIds).forEach(space -> names.put(space.getId().toString(), space.getName()));
        return names;
    }
}
