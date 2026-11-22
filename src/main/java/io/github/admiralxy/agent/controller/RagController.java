package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.documents.AddToSpaceRq;
import io.github.admiralxy.agent.controller.response.documents.GetDocumentsRs;
import io.github.admiralxy.agent.domain.RagDocument;
import io.github.admiralxy.agent.domain.RagSearchHit;
import io.github.admiralxy.agent.security.AuthUser;
import io.github.admiralxy.agent.service.model.ModelErrors;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
import io.github.admiralxy.agent.service.provider.RagGitOptions;
import io.github.admiralxy.agent.service.provider.RagProviderAuth;
import io.github.admiralxy.agent.service.rag.AddDocumentCommand;
import io.github.admiralxy.agent.service.rag.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/rag")
public class RagController {

    static final String ERROR_EVENT = "error";

    private static final long STREAM_TIMEOUT_MS = 30 * 60_000L;
    private static final String MESSAGE = "message";
    private static final String PARTIAL = "partial";
    private static final String SEND_FAILED = "Failed to send SSE event";
    private static final String INDEXING_FAILED = "Adding content to space {} failed: {}";

    private final RagService ragService;
    private final ThreadPoolTaskExecutor taskExecutor;

    /**
     * Streams the progress in percent; a failure ends the stream with an {@code error} event
     * {@code {"message": "...", "partial": true|false}}, where {@code partial} tells that some chunks were stored.
     */
    @PostMapping(value = "/{space}/documents/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter addStream(@AuthenticationPrincipal AuthUser user, @PathVariable UUID space,
                                @RequestBody AddToSpaceRq rq) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AddDocumentCommand command = toCommand(user.id(), space, rq);
        taskExecutor.execute(() -> subscribeToProgress(emitter, Flux.defer(() -> ragService.add(command))));
        return emitter;
    }

    @DeleteMapping("/{space}/documents/{docId}")
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable UUID space, @PathVariable String docId) {
        ragService.deleteDocument(user.id(), space, docId);
    }

    @DeleteMapping("/{space}/documents/{docId}/chunks/{chunkId}")
    public void deleteChunk(@AuthenticationPrincipal AuthUser user, @PathVariable UUID space,
                            @PathVariable String docId, @PathVariable String chunkId) {
        ragService.deleteChunk(user.id(), space, docId, chunkId);
    }

    @GetMapping("/{space}/search")
    public GetDocumentsRs search(@AuthenticationPrincipal AuthUser user,
                                 @PathVariable UUID space,
                                 @RequestParam String q,
                                 @RequestParam(defaultValue = "5") int k) {
        return new GetDocumentsRs(toDocuments(ragService.search(user.id(), space, q, k)));
    }

    @GetMapping("/{space}/documents")
    public GetDocumentsRs listDocuments(@AuthenticationPrincipal AuthUser user,
                                        @PathVariable UUID space,
                                        @RequestParam(defaultValue = "16") int limit) {
        return new GetDocumentsRs(toDocuments(ragService.listDocuments(user.id(), space, limit)));
    }

    void subscribeToProgress(SseEmitter emitter, Flux<Integer> progress) {
        AtomicInteger sent = new AtomicInteger();
        progress
                .doOnNext(percent -> {
                    send(emitter, SseEmitter.event().data(percent));
                    sent.incrementAndGet();
                })
                .subscribe(ignored -> {
                }, error -> fail(emitter, error, sent.get() > 0), emitter::complete);
    }

    private static void fail(SseEmitter emitter, Throwable error, boolean partial) {
        String message = ModelErrors.describe(error);
        log.warn(INDEXING_FAILED, emitter, message);
        try {
            emitter.send(SseEmitter.event().name(ERROR_EVENT)
                    .data(Map.of(MESSAGE, message, PARTIAL, partial), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException | RuntimeException e) {
            emitter.completeWithError(error);
        }
    }

    private static void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException e) {
            throw new IllegalStateException(SEND_FAILED, e);
        }
    }

    private static List<RagDocument> toDocuments(List<RagSearchHit> hits) {
        return hits.stream().map(hit -> new RagDocument(hit.id(), hit.content(), hit.metadata())).toList();
    }

    private static AddDocumentCommand toCommand(UUID userId, UUID space, AddToSpaceRq rq) {
        RagGitOptions git = rq.git() == null ? null : new RagGitOptions(rq.git().branch(), rq.git().folder());
        RagProviderAuth auth = rq.auth() == null ? null : new RagProviderAuth(rq.auth().login(), rq.auth().password());
        return new AddDocumentCommand(userId, space, rq.providerType(),
                new RagContentRequest(rq.text(), rq.batch(), git, auth));
    }
}
