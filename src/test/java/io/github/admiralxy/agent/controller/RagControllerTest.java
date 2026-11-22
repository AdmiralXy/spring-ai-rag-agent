package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.service.rag.RagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RagControllerTest {

    @Mock
    private RagService ragService;

    @Mock
    private ThreadPoolTaskExecutor taskExecutor;

    @Mock
    private SseEmitter emitter;

    @Test
    void subscribeToProgressCompletesWhenFluxSucceeds() throws IOException {
        RagController ragController = new RagController(ragService, taskExecutor);

        ragController.subscribeToProgress(emitter, Flux.just(10, 100));

        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }

    @Test
    void subscribeToProgressSendsReadableErrorEventWhenFluxFails() throws IOException {
        RagController ragController = new RagController(ragService, taskExecutor);

        ragController.subscribeToProgress(emitter, Flux.error(new IllegalStateException("boom")));

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any());
    }

    @Test
    void subscribeToProgressCompletesWithErrorWhenSendFails() throws IOException {
        RagController ragController = new RagController(ragService, taskExecutor);
        doThrow(new IOException("io failed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        ragController.subscribeToProgress(emitter, Flux.just(10));

        verify(emitter).completeWithError(any(IllegalStateException.class));
    }
}
