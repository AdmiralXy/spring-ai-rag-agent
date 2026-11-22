package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.ProviderType;
import io.github.admiralxy.agent.domain.RagSearchHit;
import io.github.admiralxy.agent.exception.ApiException;
import io.github.admiralxy.agent.repository.RagDocumentRepository;
import io.github.admiralxy.agent.repository.RagDocumentRepository.NewChunk;
import io.github.admiralxy.agent.service.model.EmbeddingModelRuntime;
import io.github.admiralxy.agent.service.model.EmbeddingModelRuntime.Embedder;
import io.github.admiralxy.agent.service.model.ModelErrors;
import io.github.admiralxy.agent.service.provider.RagChunk;
import io.github.admiralxy.agent.service.provider.RagContentProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
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

    private static final List<String> TOKEN_LIMIT_ERRORS = List.of(
            "maximum number of allowed input tokens", "maximum context length", "too many tokens",
            "input is too long", "tokens exceeds"
    );
    private static final String NO_PROVIDER = "No content provider for type: %s";
    private static final String CANNOT_SPLIT = "A chunk exceeds the input limit of the embedding model and cannot "
            + "be split; lower \"Max document tokens\" in Settings → Models → Embeddings";
    private static final String EMBEDDING_FAILED = "The embedding model %s at %s did not respond: %s. Check Settings "
            + "→ Models → Embeddings.";
    private static final int MIN_BUDGET_TOKENS = 16;
    private static final int COMPLETE = 100;

    private final RagDocumentRepository repository;
    private final TokenizerService tokenizerService;
    private final EmbeddingModelRuntime embeddingModelRuntime;
    private final List<RagContentProvider> contentProviders;

    public Flux<Integer> index(AddDocumentCommand command) {
        return Flux.defer(() -> {
            Embedder embedder = embeddingModelRuntime.forUser(command.userId());
            Map<String, Object> meta = Map.of(
                    RagSearchHit.SPACE, command.spaceId().toString(),
                    RagSearchHit.DOC, UUID.randomUUID().toString()
            );
            Target target = new Target(command.spaceId(), embedder, meta);
            return provider(command.providerType())
                    .resolveChunks(command.contentRequest())
                    .map(chunk -> store(target, chunk))
                    .switchIfEmpty(Flux.just(COMPLETE));
        });
    }

    private int store(Target target, RagChunk chunk) {
        String header = chunk.source() == null ? StringUtils.EMPTY
                : RagContextAssembler.SOURCE_HEADER.formatted(chunk.source());
        int maxTokens = target.embedder().settings().getMaxDocumentTokens();
        int budget = header.isEmpty()
                ? maxTokens
                : Math.max(MIN_BUDGET_TOKENS, maxTokens - tokenizerService.countTokens(header));
        List<String> texts = tokenizerService.splitToTokenChunks(chunk.text(), budget).stream()
                .filter(StringUtils::isNotBlank)
                .map(part -> header + part)
                .toList();
        if (!texts.isEmpty()) {
            addWithFallback(target, chunk, texts);
        }
        return chunk.total() <= 0 ? COMPLETE : (int) ((chunk.number() + 1) / (double) chunk.total() * COMPLETE);
    }

    private void addWithFallback(Target target, RagChunk chunk, List<String> texts) {
        try {
            save(target, chunk, texts);
        } catch (RuntimeException e) {
            if (!isTokenLimitError(e)) {
                throw readable(target, e);
            }
            boolean knownTooLarge = texts.size() == 1;
            texts.forEach(text -> addSplitting(target, chunk, text, knownTooLarge));
        }
    }

    private void addSplitting(Target target, RagChunk chunk, String text, boolean knownTooLarge) {
        Queue<String> queue = new ArrayDeque<>(knownTooLarge ? halve(text) : List.of(text));
        while (!queue.isEmpty()) {
            String part = queue.poll();
            try {
                save(target, chunk, List.of(part));
            } catch (RuntimeException e) {
                if (!isTokenLimitError(e)) {
                    throw readable(target, e);
                }
                queue.addAll(halve(part));
            }
        }
    }

    private void save(Target target, RagChunk chunk, List<String> texts) {
        List<float[]> vectors = target.embedder().embed(texts);
        List<NewChunk> rows = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            UUID id = UUID.randomUUID();
            rows.add(new NewChunk(id, texts.get(i), metadata(target.meta(), chunk, id), vectors.get(i)));
        }
        repository.insert(target.spaceId(), target.embedder().settings().getDimensions(), rows);
    }

    private List<String> halve(String text) {
        int tokens = tokenizerService.countTokens(text);
        List<String> parts = tokenizerService.splitToTokenChunks(text, Math.max(1, tokens / 2));
        if (tokens <= 1 || parts.size() <= 1) {
            throw ApiException.badRequest(CANNOT_SPLIT);
        }
        return parts;
    }

    private static RuntimeException readable(Target target, RuntimeException e) {
        if (e instanceof ApiException) {
            return e;
        }
        var settings = target.embedder().settings();
        return new ApiException(HttpStatus.BAD_GATEWAY, EMBEDDING_FAILED.formatted(settings.getName(),
                host(settings.getBaseUrl()), ModelErrors.describe(e)));
    }

    private static String host(String baseUrl) {
        try {
            String host = URI.create(baseUrl.trim()).getHost();
            return host == null ? baseUrl : host;
        } catch (IllegalArgumentException e) {
            return baseUrl;
        }
    }

    private static Map<String, Object> metadata(Map<String, Object> meta, RagChunk chunk, UUID id) {
        Map<String, Object> metadata = new HashMap<>(meta);
        metadata.put(RagSearchHit.CHUNK, id.toString());
        metadata.put(RagSearchHit.NUMBER, chunk.number());
        metadata.put(RagSearchHit.TOTAL, chunk.total());
        metadata.put(RagSearchHit.PART, chunk.part());
        metadata.put(RagSearchHit.PARTS, chunk.parts());
        if (chunk.source() != null) {
            metadata.put(RagSearchHit.SOURCE, chunk.source());
        }
        return metadata;
    }

    static boolean isTokenLimitError(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause()) {
            String message = e.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (TOKEN_LIMIT_ERRORS.stream().anyMatch(lower::contains)) {
                    return true;
                }
            }
        }
        return false;
    }

    private RagContentProvider provider(ProviderType providerType) {
        ProviderType type = providerType == null ? ProviderType.TEXT : providerType;
        return contentProviders.stream()
                .filter(provider -> provider.supports(type))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(NO_PROVIDER.formatted(type)));
    }

    private record Target(UUID spaceId, Embedder embedder, Map<String, Object> meta) {
    }
}
