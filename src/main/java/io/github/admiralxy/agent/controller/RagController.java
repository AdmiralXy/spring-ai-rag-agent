package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.documents.AddToSpaceRq;
import io.github.admiralxy.agent.controller.response.documents.GetDocumentsRs;
import io.github.admiralxy.agent.domain.RagDocument;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
import io.github.admiralxy.agent.service.provider.RagGitOptions;
import io.github.admiralxy.agent.service.provider.RagProviderAuth;
import io.github.admiralxy.agent.service.rag.AddDocumentCommand;
import io.github.admiralxy.agent.service.rag.RagService;
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
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rag")
public class RagController {

    private static final long STREAM_TIMEOUT_MS = 240_000L;
    private static final String SEND_FAILED = "Failed to send SSE event";

    private final RagService ragService;
    private final ThreadPoolTaskExecutor taskExecutor;

    @PostMapping("/{space}/documents/stream")
    public SseEmitter addStream(@PathVariable String space, @RequestBody AddToSpaceRq rq) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        taskExecutor.execute(() -> {
            try {
                subscribeToProgress(emitter, ragService.add(toCommand(space, rq)));
            } catch (RuntimeException e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @DeleteMapping("/{space}/documents/{docId}")
    public void delete(@PathVariable String space, @PathVariable String docId) {
        ragService.deleteDocument(space, docId);
    }

    @DeleteMapping("/{space}/documents/{docId}/chunks/{chunkId}")
    public void deleteChunk(@PathVariable String space, @PathVariable String docId, @PathVariable String chunkId) {
        ragService.deleteChunk(space, docId, chunkId);
    }

    @GetMapping("/{space}/search")
    public GetDocumentsRs search(@PathVariable String space,
                                 @RequestParam String q,
                                 @RequestParam(defaultValue = "5") int k) {
        List<RagDocument> documents = ragService.search(space, q, k).stream()
                .map(d -> new RagDocument(d.getId(), d.getText(), d.getMetadata()))
                .toList();
        return new GetDocumentsRs(documents);
    }

    @GetMapping("/{space}/documents")
    public GetDocumentsRs listDocuments(@PathVariable String space,
                                        @RequestParam(defaultValue = "16") int limit) {
        List<RagDocument> documents = ragService.listDocuments(space, limit).stream()
                .map(hit -> new RagDocument(hit.id(), hit.content(), hit.metadata()))
                .toList();
        return new GetDocumentsRs(documents);
    }

    void subscribeToProgress(SseEmitter emitter, Flux<Integer> progress) {
        progress
                .doOnNext(percent -> {
                    try {
                        emitter.send(SseEmitter.event().data(percent));
                    } catch (IOException e) {
                        throw new IllegalStateException(SEND_FAILED, e);
                    }
                })
                .subscribe(ignored -> {
                }, emitter::completeWithError, emitter::complete);
    }

    private static AddDocumentCommand toCommand(String space, AddToSpaceRq rq) {
        RagGitOptions git = rq.git() == null ? null : new RagGitOptions(rq.git().branch(), rq.git().folder());
        RagProviderAuth auth = rq.auth() == null ? null : new RagProviderAuth(rq.auth().login(), rq.auth().password());
        return new AddDocumentCommand(space, rq.providerType(), new RagContentRequest(rq.text(), rq.batch(), git, auth));
    }
}
