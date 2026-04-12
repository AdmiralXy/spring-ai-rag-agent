package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.service.AddDocumentCommand;
import io.github.admiralxy.agent.service.RagService;
import io.github.admiralxy.agent.service.TokenizerService;
import io.github.admiralxy.agent.service.provider.RagChunk;
import io.github.admiralxy.agent.service.provider.RagContentProvider;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
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
    private static final String TOKEN_LIMIT_ERROR_FRAGMENT = "maximum number of allowed input tokens";

    private final VectorStore store;
    private final TokenizerService tokenizerService;
    private final RagProperties ragProperties;
    private final List<RagContentProvider> contentProviders;

    @Override
    public Flux<Integer> add(AddDocumentCommand command) {
        String spaceId = command.spaceId();
        String docId = UUID.randomUUID().toString();
        Map<String, Object> meta = Map.of(
                SPACE_METADATA_KEY, spaceId,
                ID_METADATA_KEY, docId
        );
        RagContentRequest request = command.contentRequest();

        return resolveProvider(command.providerType())
                .resolveChunks(request)
                .map(chunk -> persistAndMapProgress(meta, chunk))
                .switchIfEmpty(Flux.just(100));
    }

    private int persistAndMapProgress(Map<String, Object> meta, RagChunk chunk) {
        List<String> tokenSafeParts = tokenizerService.splitToTokenChunks(
                chunk.text(),
                ragProperties.getMaxDocumentTokens()
        );
        tokenSafeParts.forEach(part -> saveWithTokenLimitFallback(meta, chunk, part));

        if (chunk.total() <= 0) {
            return 100;
        }
        return (int) (((chunk.number() + 1) / (double) chunk.total()) * 100);
    }

    private void saveWithTokenLimitFallback(Map<String, Object> meta, RagChunk chunk, String text) {
        Queue<String> queue = new ArrayDeque<>();
        queue.add(text);

        while (!queue.isEmpty()) {
            String part = queue.poll();
            try {
                store.add(List.of(newDocument(meta, chunk, part)));
            } catch (RuntimeException ex) {
                if (!isTokenLimitError(ex)) {
                    throw ex;
                }

                int tokens = tokenizerService.countTokens(part);
                if (tokens <= 1) {
                    throw ex;
                }

                List<String> split = tokenizerService.splitToTokenChunks(part, Math.max(1, tokens / 2));
                if (split.size() <= 1) {
                    throw ex;
                }
                queue.addAll(split);
            }
        }
    }

    private Document newDocument(Map<String, Object> meta, RagChunk chunk, String text) {
        String chunkId = UUID.randomUUID().toString();
        Map<String, Object> metaChunk = new HashMap<>(meta);
        metaChunk.put(CHUNK_NUMBER_METADATA_KEY, chunk.number());
        metaChunk.put(TOTAL_CHUNKS_METADATA_KEY, chunk.total());
        metaChunk.put(CHUNK_ID_METADATA_KEY, chunkId);
        return new Document(chunkId, text, metaChunk);
    }

    private boolean isTokenLimitError(RuntimeException ex) {
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains(TOKEN_LIMIT_ERROR_FRAGMENT);
    }

    private RagContentProvider resolveProvider(ProviderType providerType) {
        ProviderType effectiveProvider = providerType == null ? ProviderType.TEXT : providerType;
        return contentProviders.stream()
                .filter(provider -> provider.supports(effectiveProvider))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No content provider for type: " + effectiveProvider));
    }

    @Override
    public void delete(String docId) {
        store.delete(List.of(docId));
    }

    @Override
    public List<Document> search(String spaceId, String query, int topK) {
        return store.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression(SPACE_FILTER_EXPRESSION_TEMPLATE.formatted(spaceId))
                        .build()
        );
    }

    @Override
    public List<Document> listDocuments(String spaceId, int limit) {
        return store.similaritySearch(
                SearchRequest.builder()
                        .query(" ")
                        .topK(limit)
                        .filterExpression(SPACE_FILTER_EXPRESSION_TEMPLATE.formatted(spaceId))
                        .build()
        );
    }

    @Override
    public void deleteFromSpace(String spaceId) {
        var docs = store.similaritySearch(
                SearchRequest.builder()
                        .query(StringUtils.SPACE)
                        .topK(Integer.MAX_VALUE)
                        .filterExpression(SPACE_FILTER_EXPRESSION_TEMPLATE.formatted(spaceId))
                        .build()
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
                SearchRequest.builder()
                        .query(StringUtils.SPACE)
                        .topK(Integer.MAX_VALUE)
                        .filterExpression(ID_SPACE_FILTER_EXPRESSION_TEMPLATE.formatted(docId, spaceId))
                        .build()
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
                SearchRequest.builder()
                        .query(StringUtils.SPACE)
                        .topK(1)
                        .filterExpression(ID_CHUNK_SPACE_FILTER_EXPRESSION_TEMPLATE.formatted(docId, chunkId, spaceId))
                        .build()
        );
        if (!docs.isEmpty()) {
            List<String> ids = docs.stream()
                    .map(Document::getId)
                    .toList();
            store.delete(ids);
        }
    }

    @Override
    public String buildContext(String spaceId, String query, double percentage, int maxTokens, int topK) {
        List<Document> docs = store.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression(SPACE_FILTER_EXPRESSION_TEMPLATE.formatted(spaceId))
                        .build()
        );

        if (docs.isEmpty()) {
            return StringUtils.EMPTY;
        }

        int totalTokens = docs.stream()
                .mapToInt(d -> tokenizerService.countTokens(d.getText()))
                .sum();

        int targetTokens = totalTokens;
        if (totalTokens > maxTokens) {
            targetTokens = (int) (totalTokens * (percentage / 100.0));
            targetTokens = Math.min(maxTokens, targetTokens);
        }

        StringBuilder sb = new StringBuilder();
        int used = 0;

        for (Document doc : docs) {
            String content = doc.getText();
            int len = tokenizerService.countTokens(content);

            if (used + len > targetTokens) {
                int remaining = targetTokens - used;
                if (remaining > 0) {
                    sb.append(tokenizerService.truncateToTokens(content, remaining));
                }
                break;
            }

            sb.append(content).append(CONTENT_CONTENT_SEPARATOR);
            used += len;
        }

        return sb.toString();
    }
}
