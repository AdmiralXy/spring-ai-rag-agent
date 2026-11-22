package io.github.admiralxy.agent.service.provider;

import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.service.rag.TextChunkerService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public abstract class AbstractChunkingRagContentProvider implements RagContentProvider {

    private final TextChunkerService textChunkerService;
    private final RagProperties.Chunking chunking;

    protected AbstractChunkingRagContentProvider(TextChunkerService textChunkerService, RagProperties ragProperties) {
        this.textChunkerService = textChunkerService;
        this.chunking = ragProperties.getChunking();
    }

    @Override
    public Flux<RagChunk> resolveChunks(RagContentRequest request) {
        return resolveContent(request)
                .flatMapMany(content -> {
                    List<String> chunks = request.batch()
                            ? textChunkerService.chunk(content.text(), chunking.getMaxLines(), chunking.getMaxChars(),
                                    chunking.getOverlapLines(), chunking.getOverlapWords())
                            : List.of(content.text());
                    int total = chunks.size();
                    return Flux.range(0, total)
                            .map(i -> new RagChunk(chunks.get(i), i, total, content.source(), i, total));
                });
    }

    protected abstract Mono<RagContent> resolveContent(RagContentRequest request);
}
