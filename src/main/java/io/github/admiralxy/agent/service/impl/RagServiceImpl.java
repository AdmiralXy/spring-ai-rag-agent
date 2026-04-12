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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private static final String CONTENT_CONTENT_SEPARATOR = "\n---\n";

    private static final String SPACE_FILTER_EXPRESSION_TEMPLATE = "space == '%s'";
    private static final String SPACE_FILTER_PART_EXPRESSION_TEMPLATE = "space == '%s'";
    private static final String ID_SPACE_FILTER_EXPRESSION_TEMPLATE = "doc == '%s' && space == '%s'";
    private static final String ID_CHUNK_SPACE_FILTER_EXPRESSION_TEMPLATE = "doc == '%s' && chunk == '%s' && space == '%s'";

    private static final String ID_METADATA_KEY = "doc";
    private static final String CHUNK_ID_METADATA_KEY = "chunk";
    private static final String SPACE_METADATA_KEY = "space";
    private static final String CHUNK_NUMBER_METADATA_KEY = "number";
    private static final String TOTAL_CHUNKS_METADATA_KEY = "total";
    private static final String TOKEN_LIMIT_ERROR_FRAGMENT = "maximum number of allowed input tokens";
    private static final int MAX_CONTEXT_OVERFETCH = 200;
    private static final int REBALANCE_CANDIDATE_WINDOW_MULTIPLIER = 2;
    private static final String UNKNOWN_SOURCE_VALUE = "unknown";
    private static final String SOURCE_HEADER_TEMPLATE = "[Source: space=%s, doc=%s, chunk=%s]";

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
    public String buildContext(List<String> spaceIds, String query, double percentage, int maxTokens, int topK) {
        List<Document> docs = findContextDocuments(spaceIds, query, topK);

        if (docs.isEmpty()) {
            return StringUtils.EMPTY;
        }

        int totalTokens = docs.stream()
                .mapToInt(d -> tokenizerService.countTokens(d.getText()))
                .sum();

        int hardLimit = Math.min(maxTokens, totalTokens);
        double normalizedPercentage = Math.clamp(percentage, 0.0, 100.0);
        int targetTokens = (int) Math.floor(hardLimit * (normalizedPercentage / 100.0));
        if (hardLimit > 0 && targetTokens == 0 && normalizedPercentage > 0) {
            targetTokens = 1;
        }

        StringBuilder sb = new StringBuilder();
        int used = 0;

        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            int docsLeft = docs.size() - i;
            int remaining = targetTokens - used;
            if (remaining <= 0) {
                break;
            }

            int perDocumentBudget = Math.max(1, remaining / docsLeft);
            String content = doc.getText();
            int len = tokenizerService.countTokens(content);
            String contentPart = len > perDocumentBudget
                    ? tokenizerService.truncateToTokens(content, perDocumentBudget)
                    : content;

            if (StringUtils.isBlank(contentPart)) {
                continue;
            }

            sb.append(buildSourceHeader(doc))
                    .append(StringUtils.LF)
                    .append(contentPart)
                    .append(CONTENT_CONTENT_SEPARATOR);
            used += tokenizerService.countTokens(contentPart);
        }

        return sb.toString();
    }

    private List<Document> findContextDocuments(List<String> spaceIds, String query, int topK) {
        if (topK <= 0) {
            return Collections.emptyList();
        }

        List<String> normalizedSpaceIds = normalizeSpaceIds(spaceIds);
        if (normalizedSpaceIds.isEmpty()) {
            return Collections.emptyList();
        }

        long requestedOverfetch = (long) topK * normalizedSpaceIds.size();
        int overfetch = (int) Math.clamp(requestedOverfetch, topK, (long) MAX_CONTEXT_OVERFETCH);
        List<Document> docs = store.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(overfetch)
                        .filterExpression(buildMultiSpaceFilterExpression(normalizedSpaceIds))
                        .build()
        );

        if (docs.size() <= topK || normalizedSpaceIds.size() == 1) {
            return docs.stream().limit(topK).toList();
        }

        // Preserve global relevance order and only lightly improve source coverage.
        List<Document> selected = new ArrayList<>(docs.stream().limit(topK).toList());
        rebalanceTailForMissingSpaces(selected, docs, normalizedSpaceIds, topK);
        return selected;
    }

    private void rebalanceTailForMissingSpaces(List<Document> selected, List<Document> rankedDocs, List<String> spaceIds, int topK) {
        Set<String> presentSpaces = new HashSet<>();
        selected.stream()
                .map(this::extractSpaceId)
                .filter(Objects::nonNull)
                .forEach(presentSpaces::add);

        List<String> missingSpaces = spaceIds.stream()
                .filter(spaceId -> !presentSpaces.contains(spaceId))
                .toList();
        if (missingSpaces.isEmpty()) {
            return;
        }

        Map<String, Document> firstByMissingSpace = new LinkedHashMap<>();
        int candidateWindow = (int) Math.clamp(
                (long) topK * REBALANCE_CANDIDATE_WINDOW_MULTIPLIER,
                topK,
                (long) rankedDocs.size()
        );
        for (int i = 0; i < candidateWindow; i++) {
            Document doc = rankedDocs.get(i);
            String spaceId = extractSpaceId(doc);
            if (spaceId != null
                    && !firstByMissingSpace.containsKey(spaceId)
                    && missingSpaces.contains(spaceId)) {
                firstByMissingSpace.put(spaceId, doc);
            }
        }

        if (firstByMissingSpace.isEmpty()) {
            return;
        }

        Map<String, Integer> selectedCountBySpace = new HashMap<>();
        for (Document doc : selected) {
            String spaceId = extractSpaceId(doc);
            if (spaceId == null) {
                continue;
            }
            selectedCountBySpace.merge(spaceId, 1, Integer::sum);
        }

        for (String missingSpace : missingSpaces) {
            Document candidate = firstByMissingSpace.get(missingSpace);
            if (candidate == null) {
                continue;
            }

            int replaceIdx = findTailIndexForReplacement(selected, selectedCountBySpace, candidate.getId());
            if (replaceIdx < 0) {
                continue;
            }

            Document replaced = selected.set(replaceIdx, candidate);
            String replacedSpace = extractSpaceId(replaced);
            if (replacedSpace != null) {
                selectedCountBySpace.computeIfPresent(replacedSpace, (ignoredKey, v) -> Math.max(0, v - 1));
            }
            selectedCountBySpace.merge(missingSpace, 1, Integer::sum);
        }
    }

    private int findTailIndexForReplacement(List<Document> selected, Map<String, Integer> countBySpace, String candidateId) {
        Set<String> selectedIds = selected.stream().map(Document::getId).collect(java.util.stream.Collectors.toSet());
        if (selectedIds.contains(candidateId)) {
            return -1;
        }

        for (int i = selected.size() - 1; i >= 0; i--) {
            Document doc = selected.get(i);
            String spaceId = extractSpaceId(doc);
            if (spaceId == null) {
                continue;
            }
            if (countBySpace.getOrDefault(spaceId, 0) > 1) {
                return i;
            }
        }
        return -1;
    }

    private List<String> normalizeSpaceIds(List<String> spaceIds) {
        if (spaceIds == null || spaceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return spaceIds.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String buildMultiSpaceFilterExpression(List<String> spaceIds) {
        return spaceIds.stream()
                .map(spaceId -> SPACE_FILTER_PART_EXPRESSION_TEMPLATE.formatted(spaceId.replace("'", "''")))
                .reduce((left, right) -> left + " || " + right)
                .orElse("1 == 0");
    }

    private String extractSpaceId(Document doc) {
        Object raw = doc.getMetadata().get(SPACE_METADATA_KEY);
        return raw == null ? null : String.valueOf(raw);
    }

    private String buildSourceHeader(Document doc) {
        String space = valueOrUnknown(doc.getMetadata().get(SPACE_METADATA_KEY));
        String sourceDocId = valueOrUnknown(doc.getMetadata().get(ID_METADATA_KEY));
        String chunk = valueOrUnknown(doc.getMetadata().get(CHUNK_ID_METADATA_KEY));
        return SOURCE_HEADER_TEMPLATE.formatted(space, sourceDocId, chunk);
    }

    private String valueOrUnknown(Object value) {
        return value == null ? UNKNOWN_SOURCE_VALUE : String.valueOf(value);
    }
}
