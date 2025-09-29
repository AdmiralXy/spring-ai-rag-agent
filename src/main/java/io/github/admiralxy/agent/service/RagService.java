package io.github.admiralxy.agent.service;

import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.util.List;

public interface RagService {

    /**
     * Add a document to a specific space.
     *
     * @param spaceId space ID
     * @param text content
     * @return stream of percentages indicating progress
     */
    Flux<Integer> add(String spaceId, String text);

    /**
     * Delete a document by its ID.
     *
     * @param docId document ID
     */
    void delete(String docId);

    /**
     * Search for documents in a specific space.
     *
     * @param spaceId space ID
     * @param query query
     * @param topK number of top results to return
     * @return list of documents
     */
    List<Document> search(String spaceId, String query, int topK);

    /**
     * List documents in a specific space.
     *
     * @param spaceId space ID
     * @param limit maximum number of documents to return
     * @return list of documents
     */
    List<Document> listDocuments(String spaceId, int limit);

    /**
     * Delete a documents from a specific space.
     *
     * @param spaceId space ID
     */
    void deleteFromSpace(String spaceId);

    /**
     * Delete a document from a specific space by its ID.
     *
     * @param spaceId space ID
     * @param docId document ID
     */
    void deleteFromSpace(String spaceId, String docId);

    /**
     * Delete a chunk of a document from a specific space.
     *
     * @param spaceId space ID
     * @param docId document ID
     * @param chunkId chunk ID
     */
    void deleteChunkFromSpace(String spaceId, String docId, String chunkId);

    /**
     * Build context from documents in a specific space based on a query.
     *
     * @param spaceId space ID
     * @param query query
     * @param percentage percentage of the document to include
     * @param maxChars maximum number of characters in the context
     * @param topK number of top documents to consider
     * @return context string
     */
    String buildContext(String spaceId, String query, double percentage, int maxChars, int topK);
}
