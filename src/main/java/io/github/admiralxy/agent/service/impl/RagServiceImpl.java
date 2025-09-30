package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.service.RagService;
import io.github.admiralxy.agent.service.TextChunkerService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private static final String CONTENT_CONTENT_SEPARATOR = "\n---\n";

    private static final String SPACE_FILTER_EXPRESSION_TEMPLATE = "space == '%s'";
    private static final String ID_SPACE_FILTER_EXPRESSION_TEMPLATE = "doc == '%s' && space == '%s'";
    private static final String ID_CHUNK_SPACE_FILTER_EXPRESSION_TEMPLATE = "doc == '%s' && chunk == '%s' && space == '%s'";

    private static final String ID_METADATA_KEY = "doc";
    private static final String CHUNK_ID_METADATA_KEY = "chunk";
    private static final String SPACE_METADATA_KEY = "space";
    private static final String CHUNK_NUMBER_METADATA_KEY = "number";
    private static final String TOTAL_CHUNKS_METADATA_KEY = "total";

    private final VectorStore store;
    private final TextChunkerService textChunkerService;

    @Override
    public Flux<Integer> add(String spaceId, String text) {
        String docId = UUID.randomUUID().toString();
        Map<String, Object> meta = Map.of(
                SPACE_METADATA_KEY, spaceId,
                ID_METADATA_KEY, docId
        );

        List<String> chunks = textChunkerService.chunk(text, 100, 1500, 50);
        int total = chunks.size();

        return Flux.create(sink -> {
            for (int i = 0; i < total; i++) {
                String chunkId = UUID.randomUUID().toString();
                String chunk = chunks.get(i);
                Map<String, Object> metaChunk = new HashMap<>(meta);
                metaChunk.put(CHUNK_NUMBER_METADATA_KEY, i);
                metaChunk.put(TOTAL_CHUNKS_METADATA_KEY, chunks.size());
                metaChunk.put(CHUNK_ID_METADATA_KEY, chunkId);

                store.add(List.of(new Document(chunkId, chunk, metaChunk)));

                int percent = (int) (((i + 1) / (double) total) * 100);
                sink.next(percent);
            }
            sink.complete();
        });
    }

    @Override
    public void delete(String docId) {
        store.delete(List.of(docId));
    }

    @Override
    public List<Document> search(String spaceId, String query, int topK) {
        return store.similaritySearch(
                SearchRequest.query(query)
                        .withTopK(topK)
                        .withFilterExpression(SPACE_FILTER_EXPRESSION_TEMPLATE.formatted(spaceId))
        );
    }

    @Override
    public List<Document> listDocuments(String spaceId, int limit) {
        return store.similaritySearch(
                SearchRequest.query(" ")
                        .withTopK(limit)
                        .withFilterExpression(SPACE_FILTER_EXPRESSION_TEMPLATE.formatted(spaceId))
        );
    }

    @Override
    public void deleteFromSpace(String spaceId) {
        var docs = store.similaritySearch(
                SearchRequest.query(StringUtils.SPACE)
                        .withTopK(Integer.MAX_VALUE)
                        .withFilterExpression(SPACE_FILTER_EXPRESSION_TEMPLATE.formatted(spaceId))
        );
        if (!docs.isEmpty()) {
            List<String> ids = docs.stream()
                    .map(Document::getId)
                    .toList();
            store.delete(ids);
        }
    }

    @Override
    public void deleteFromSpace(String spaceId, String docId) {
        var docs = store.similaritySearch(
                SearchRequest.query(StringUtils.SPACE)
                        .withTopK(Integer.MAX_VALUE)
                        .withFilterExpression(ID_SPACE_FILTER_EXPRESSION_TEMPLATE.formatted(docId, spaceId))
        );
        if (!docs.isEmpty()) {
            List<String> ids = docs.stream()
                    .map(Document::getId)
                    .toList();
            store.delete(ids);
        }
    }

    @Override
    public void deleteChunkFromSpace(String spaceId, String docId, String chunkId) {
        var docs = store.similaritySearch(
                SearchRequest.query(StringUtils.SPACE)
                        .withTopK(1)
                        .withFilterExpression(ID_CHUNK_SPACE_FILTER_EXPRESSION_TEMPLATE.formatted(docId, chunkId, spaceId))
        );
        if (!docs.isEmpty()) {
            List<String> ids = docs.stream()
                    .map(Document::getId)
                    .toList();
            store.delete(ids);
        }
    }

    @Override
    public String buildContext(String spaceId, String query, double percentage, int maxChars, int topK) {
        List<Document> docs = store.similaritySearch(
                SearchRequest.query(query)
                        .withTopK(topK)
                        .withFilterExpression(SPACE_FILTER_EXPRESSION_TEMPLATE.formatted(spaceId))
        );

        if (docs.isEmpty()) {
            return StringUtils.EMPTY;
        }

        int totalLength = docs.stream()
                .mapToInt(d -> d.getContent().length())
                .sum();

        int targetLength = (int) (totalLength * (percentage / 100.0));
        targetLength = Math.max(targetLength, maxChars / 2);
        targetLength = Math.min(maxChars, targetLength);

        StringBuilder sb = new StringBuilder();
        int used = 0;

        for (Document doc : docs) {
            String content = doc.getContent();
            int len = content.length();

            if (used > 0 && used + len > targetLength) {
                break;
            }

            sb.append(content).append(CONTENT_CONTENT_SEPARATOR);
            used += len;
        }

        return sb.toString();
    }
}
