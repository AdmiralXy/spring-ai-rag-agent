package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.domain.RagSearchHit;
import io.github.admiralxy.agent.entity.SpaceEntity;
import io.github.admiralxy.agent.exception.ApiException;
import io.github.admiralxy.agent.repository.RagDocumentRepository;
import io.github.admiralxy.agent.repository.SpaceRepository;
import io.github.admiralxy.agent.service.model.EmbeddingModelRuntime;
import io.github.admiralxy.agent.service.model.ModelErrors;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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

    static final String SPACE_NOT_FOUND = "Space not found: %s";

    private static final int FOLLOW_UP_MAX_TOKENS = 24;
    private static final String SEARCH_FAILED = "Knowledge base search failed: %s. Check Settings → Models → "
            + "Embeddings.";

    private final RagIndexer indexer;
    private final RagDocumentRepository repository;
    private final HybridRagRetriever retriever;
    private final RagContextAssembler assembler;
    private final RagProperties ragProperties;
    private final TokenizerService tokenizerService;
    private final SpaceRepository spaceRepository;
    private final EmbeddingModelRuntime embeddingModelRuntime;

    @Override
    public Flux<Integer> add(AddDocumentCommand command) {
        return Flux.defer(() -> {
            requireSpace(command.userId(), command.spaceId());
            return indexer.index(command);
        });
    }

    @Override
    public List<RagSearchHit> search(UUID userId, UUID spaceId, String query, int topK) {
        requireSpace(userId, spaceId);
        if (StringUtils.isBlank(query) || topK <= 0) {
            return List.of();
        }
        EmbeddingModelRuntime.Embedder embedder = embeddingModelRuntime.forUser(userId);
        try {
            return repository.vectorSearch(List.of(spaceId), embedder.settings().getDimensions(),
                    embedder.embed(query), topK);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApiException.badRequest(SEARCH_FAILED.formatted(ModelErrors.describe(e)));
        }
    }

    @Override
    public List<RagSearchHit> listDocuments(UUID userId, UUID spaceId, int limit) {
        requireSpace(userId, spaceId);
        return repository.list(spaceId, limit);
    }

    @Override
    public void deleteDocument(UUID userId, UUID spaceId, String docId) {
        requireSpace(userId, spaceId);
        repository.deleteDocument(spaceId, docId);
    }

    @Override
    public void deleteChunk(UUID userId, UUID spaceId, String docId, String chunkId) {
        requireSpace(userId, spaceId);
        repository.deleteChunk(spaceId, docId, chunkId);
    }

    @Override
    public String buildContext(UUID userId, Collection<UUID> spaceIds, String query, @Nullable String previousQuery,
                               int maxTokens) {
        if (spaceIds == null || spaceIds.isEmpty() || maxTokens <= 0 || StringUtils.isBlank(query)) {
            return StringUtils.EMPTY;
        }
        Map<String, String> spaceNames = spaceNames(userId, spaceIds);
        if (spaceNames.isEmpty()) {
            return StringUtils.EMPTY;
        }
        List<UUID> spaces = spaceNames.keySet().stream().map(UUID::fromString).toList();
        List<RagSearchHit> ranked;
        try {
            ranked = retriever.search(embeddingModelRuntime.forUser(userId), spaces, query,
                    contextualQuery(query, previousQuery), ragProperties.getTopK(), ragProperties.getKeywordWeight());
        } catch (RuntimeException e) {
            throw new IllegalStateException(SEARCH_FAILED.formatted(ModelErrors.describe(e)), e);
        }
        if (ranked.isEmpty()) {
            return StringUtils.EMPTY;
        }
        return assembler.assemble(ranked, retriever::neighbours, spaceNames, maxTokens,
                ragProperties.getExpandTopHits());
    }

    private void requireSpace(UUID userId, UUID spaceId) {
        if (spaceId == null || !spaceRepository.existsByIdAndUserId(spaceId, userId)) {
            throw ApiException.notFound(SPACE_NOT_FOUND.formatted(spaceId));
        }
    }

    @Nullable
    private String contextualQuery(String query, @Nullable String previousQuery) {
        if (StringUtils.isBlank(previousQuery) || tokenizerService.countTokens(query) > FOLLOW_UP_MAX_TOKENS) {
            return null;
        }
        return previousQuery.strip() + StringUtils.LF + query.strip();
    }

    /**
     * Names of the spaces that belong to the user; foreign ids are dropped.
     */
    private Map<String, String> spaceNames(UUID userId, Collection<UUID> spaceIds) {
        Map<String, String> names = new HashMap<>();
        for (SpaceEntity space : spaceRepository.findByUserIdAndIdIn(userId, spaceIds)) {
            names.put(space.getId().toString(), space.getName());
        }
        return names;
    }
}
