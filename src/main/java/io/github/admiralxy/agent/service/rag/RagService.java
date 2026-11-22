package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.RagSearchHit;
import jakarta.annotation.Nullable;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RagService {

    Flux<Integer> add(AddDocumentCommand command);

    List<Document> search(String spaceId, String query, int topK);

    List<RagSearchHit> listDocuments(String spaceId, int limit);

    void deleteSpace(String spaceId);

    void deleteDocument(String spaceId, String docId);

    void deleteChunk(String spaceId, String docId, String chunkId);

    String buildContext(Collection<UUID> spaceIds, String query, @Nullable String previousQuery, int maxTokens);
}
