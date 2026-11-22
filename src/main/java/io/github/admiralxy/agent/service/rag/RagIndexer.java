package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.ProviderType;
import io.github.admiralxy.agent.domain.RagSearchHit;
import io.github.admiralxy.agent.service.model.ModelSettingsService;
import io.github.admiralxy.agent.service.provider.RagChunk;
import io.github.admiralxy.agent.service.provider.RagContentProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RagIndexer {

    private static final String TOKEN_LIMIT_ERROR = "maximum number of allowed input tokens";
    private static final String NO_PROVIDER = "No content provider for type: %s";
    private static final String CANNOT_SPLIT = "A chunk exceeds the embedding model input and cannot be split";
    private static final int MIN_BUDGET_TOKENS = 16;
    private static final int COMPLETE = 100;

    private final VectorStore store;
    private final TokenizerService tokenizerService;
    private final ModelSettingsService modelSettingsService;
    private final List<RagContentProvider> contentProviders;

    public Flux<Integer> index(AddDocumentCommand command) {
        Map<String, Object> meta = Map.of(
                RagSearchHit.SPACE, command.spaceId(),
                RagSearchHit.DOC, UUID.randomUUID().toString()
        );
        return provider(command.providerType())
                .resolveChunks(command.contentRequest())
                .map(chunk -> store(meta, chunk))
                .switchIfEmpty(Flux.just(COMPLETE));
    }

    private int store(Map<String, Object> meta, RagChunk chunk) {
        String header = chunk.source() == null ? StringUtils.EMPTY
                : RagContextAssembler.SOURCE_HEADER.formatted(chunk.source());
        int maxTokens = modelSettingsService.getEmbeddingModel().getMaxDocumentTokens();
        int budget = header.isEmpty()
                ? maxTokens
                : Math.max(MIN_BUDGET_TOKENS, maxTokens - tokenizerService.countTokens(header));
        List<Document> documents = tokenizerService.splitToTokenChunks(chunk.text(), budget).stream()
                .filter(StringUtils::isNotBlank)
                .map(part -> document(meta, chunk, header + part))
                .toList();
        if (!documents.isEmpty()) {
            addWithFallback(meta, chunk, documents);
        }
        return chunk.total() <= 0 ? COMPLETE : (int) ((chunk.number() + 1) / (double) chunk.total() * COMPLETE);
    }

    private void addWithFallback(Map<String, Object> meta, RagChunk chunk, List<Document> documents) {
        try {
            store.add(documents);
        } catch (RuntimeException e) {
            if (!isTokenLimitError(e)) {
                throw e;
            }
            boolean knownTooLarge = documents.size() == 1;
            documents.forEach(document -> addSplitting(meta, chunk, document.getText(), knownTooLarge));
        }
    }

    private void addSplitting(Map<String, Object> meta, RagChunk chunk, String text, boolean knownTooLarge) {
        Queue<String> queue = new ArrayDeque<>(knownTooLarge ? halve(text) : List.of(text));
        while (!queue.isEmpty()) {
            String part = queue.poll();
            try {
                store.add(List.of(document(meta, chunk, part)));
            } catch (RuntimeException e) {
                if (!isTokenLimitError(e)) {
                    throw e;
                }
                queue.addAll(halve(part));
            }
        }
    }

    private List<String> halve(String text) {
        int tokens = tokenizerService.countTokens(text);
        List<String> parts = tokenizerService.splitToTokenChunks(text, Math.max(1, tokens / 2));
        if (tokens <= 1 || parts.size() <= 1) {
            throw new IllegalStateException(CANNOT_SPLIT);
        }
        return parts;
    }

    private static Document document(Map<String, Object> meta, RagChunk chunk, String text) {
        String chunkId = UUID.randomUUID().toString();
        Map<String, Object> metadata = new HashMap<>(meta);
        metadata.put(RagSearchHit.CHUNK, chunkId);
        metadata.put(RagSearchHit.NUMBER, chunk.number());
        metadata.put(RagSearchHit.TOTAL, chunk.total());
        metadata.put(RagSearchHit.PART, chunk.part());
        metadata.put(RagSearchHit.PARTS, chunk.parts());
        if (chunk.source() != null) {
            metadata.put(RagSearchHit.SOURCE, chunk.source());
        }
        return new Document(chunkId, text, metadata);
    }

    private static boolean isTokenLimitError(RuntimeException e) {
        return e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains(TOKEN_LIMIT_ERROR);
    }

    private RagContentProvider provider(ProviderType providerType) {
        ProviderType type = providerType == null ? ProviderType.TEXT : providerType;
        return contentProviders.stream()
                .filter(provider -> provider.supports(type))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(NO_PROVIDER.formatted(type)));
    }
}
