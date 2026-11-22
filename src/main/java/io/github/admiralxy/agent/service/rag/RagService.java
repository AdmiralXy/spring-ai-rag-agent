package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.RagSearchHit;
import jakarta.annotation.Nullable;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Knowledge base of a user; every space is checked to belong to that user.
 */
public interface RagService {

    Flux<Integer> add(AddDocumentCommand command);

    List<RagSearchHit> search(UUID userId, UUID spaceId, String query, int topK);

    List<RagSearchHit> listDocuments(UUID userId, UUID spaceId, int limit);

    void deleteDocument(UUID userId, UUID spaceId, String docId);

    void deleteChunk(UUID userId, UUID spaceId, String docId, String chunkId);

    String buildContext(UUID userId, Collection<UUID> spaceIds, String query, @Nullable String previousQuery,
                        int maxTokens);
}
