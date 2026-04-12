package io.github.admiralxy.agent.service.provider;

import io.github.admiralxy.agent.service.TextChunkerService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RequiredArgsConstructor
public abstract class AbstractChunkingRagContentProvider implements RagContentProvider {

    private final TextChunkerService textChunkerService;

    @Override
    public Flux<RagChunk> resolveChunks(RagContentRequest request) {
        return resolveContent(request)
                .flatMapMany(content -> {
                    List<String> chunks = request.batch()
                            ? textChunkerService.chunk(content, 100, 1500, 50)
                            : List.of(content);
                    int total = chunks.size();
                    return Flux.range(0, total)
                            .map(i -> new RagChunk(chunks.get(i), i, total));
                });
    }

    protected abstract Mono<String> resolveContent(RagContentRequest request);
}
