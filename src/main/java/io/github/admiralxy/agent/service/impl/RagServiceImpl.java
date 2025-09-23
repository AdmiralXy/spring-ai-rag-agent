package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.service.RagService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private static final String CONTENT_CONTENT_SEPARATOR = "\n---\n";

    private static final String SPACE_FILTER_EXPRESSION_TEMPLATE = "space == '%s'";
    private static final String ID_SPACE_FILTER_EXPRESSION_TEMPLATE = "id == '%s' && space == '%s'";

    private static final String ID_METADATA_KEY = "id";
    private static final String SPACE_METADATA_KEY = "space";

    private final VectorStore store;

    @Override
    public String add(String spaceId, String text) {
        String docId = UUID.randomUUID().toString();
        Map<String, Object> meta = new HashMap<>();
        meta.put(SPACE_METADATA_KEY, spaceId);
        meta.put(ID_METADATA_KEY, docId);

        Document doc = new Document(docId, text, meta);
        store.add(List.of(doc));
        return docId;
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
    public void deleteFromSpace(String spaceId, String docId) {
        var docs = store.similaritySearch(
                SearchRequest.query(" ")
                        .withTopK(1)
                        .withFilterExpression(ID_SPACE_FILTER_EXPRESSION_TEMPLATE.formatted(docId, spaceId))
        );
        if (!docs.isEmpty()) {
            store.delete(List.of(docId));
        }
    }

    @Override
    public void deleteFromSpace(String spaceId) {
        var docs = store.similaritySearch(
                SearchRequest.query(" ")
                        .withTopK(1)
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

        int targetLength = Math.min(maxChars, (int) (totalLength * (percentage / 100.0)));

        StringBuilder sb = new StringBuilder();
        int used = 0;

        for (Document doc : docs) {
            int len = doc.getContent().length();
            if (used + len > targetLength) {
                break;
            }
            sb.append(doc.getContent()).append(CONTENT_CONTENT_SEPARATOR);
            used += len;
        }

        return sb.toString();
    }
}
