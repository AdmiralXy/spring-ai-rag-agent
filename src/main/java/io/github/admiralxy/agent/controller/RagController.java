package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.response.documents.AddToSpaceRq;
import io.github.admiralxy.agent.controller.response.documents.GetDocumentsRs;
import io.github.admiralxy.agent.domain.RagDocument;
import io.github.admiralxy.agent.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rag")
public class RagController {

    private final RagService ragService;
    private final ThreadPoolTaskExecutor taskExecutor;

    @PostMapping("/{space}/documents/stream")
    public SseEmitter addStream(@PathVariable String space, @RequestBody AddToSpaceRq rq) {
        SseEmitter emitter = new SseEmitter(240_000L);
        CompletableFuture.runAsync(() -> {
            try {
                ragService.add(space, rq.text(), rq.batch())
                        .doOnNext(percent -> {
                            try {
                                emitter.send(SseEmitter.event().data(percent));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnComplete(emitter::complete)
                        .subscribe();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }, taskExecutor);
        return emitter;
    }

    @DeleteMapping("/{space}/documents/{docId}")
    public void delete(@PathVariable String space, @PathVariable String docId) {
        ragService.deleteFromSpace(space, docId);
    }

    @DeleteMapping("/{space}/documents/{docId}/chunks/{chunkId}")
    public void deleteChunk(@PathVariable String space, @PathVariable String docId, @PathVariable String chunkId) {
        ragService.deleteChunkFromSpace(space, docId, chunkId);
    }

    @GetMapping("/{space}/search")
    public GetDocumentsRs search(
            @PathVariable String space,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int k
    ) {
        List<RagDocument> documents = ragService.search(space, q, k).stream()
                .map(d -> new RagDocument(d.getId(), d.getContent(), d.getMetadata()))
                .toList();
        return new GetDocumentsRs(documents);
    }

    @GetMapping("/{space}/documents")
    public GetDocumentsRs listDocuments(
            @PathVariable String space,
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<RagDocument> documents = ragService.listDocuments(space, limit).stream()
                .map(d -> new RagDocument(d.getId(),
                        d.getContent().substring(0, Math.min(3000, d.getContent().length())) + "...",
                        d.getMetadata()))
                .toList();

        return new GetDocumentsRs(documents);
    }
}
