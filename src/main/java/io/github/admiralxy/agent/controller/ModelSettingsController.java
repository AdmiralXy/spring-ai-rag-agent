package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.mapper.ModelSettingsMapper;
import io.github.admiralxy.agent.controller.request.settings.ChatModelUpsertRq;
import io.github.admiralxy.agent.controller.request.settings.EmbeddingsModelUpdateRq;
import io.github.admiralxy.agent.controller.request.settings.SummarizerModelUpdateRq;
import io.github.admiralxy.agent.controller.response.settings.ChatModelRs;
import io.github.admiralxy.agent.controller.response.settings.ChatModelsRs;
import io.github.admiralxy.agent.controller.response.settings.EmbeddingsModelRs;
import io.github.admiralxy.agent.controller.response.settings.SummarizerModelRs;
import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import io.github.admiralxy.agent.security.AuthUser;
import io.github.admiralxy.agent.service.model.ModelSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settings/models")
public class ModelSettingsController {

    private final ModelSettingsService modelSettingsService;
    private final ModelSettingsMapper mapper;

    @GetMapping("/chat")
    public ChatModelsRs getChatModels(@AuthenticationPrincipal AuthUser user) {
        return new ChatModelsRs(modelSettingsService.getChatModels(user.id()).stream().map(mapper::toRs).toList());
    }

    @PostMapping("/chat")
    public ChatModelRs createChatModel(@AuthenticationPrincipal AuthUser user, @RequestBody ChatModelUpsertRq rq) {
        return mapper.toRs(modelSettingsService.createChatModel(user.id(), entity -> mapper.apply(entity, rq)));
    }

    @PutMapping("/chat/{id}")
    public ChatModelRs updateChatModel(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                                       @RequestBody ChatModelUpsertRq rq) {
        return mapper.toRs(modelSettingsService.updateChatModel(user.id(), id, entity -> mapper.apply(entity, rq)));
    }

    @DeleteMapping("/chat/{id}")
    public void deleteChatModel(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        modelSettingsService.deleteChatModel(user.id(), id);
    }

    @GetMapping("/embeddings")
    public EmbeddingsModelRs getEmbeddingsModel(@AuthenticationPrincipal AuthUser user) {
        return toRs(user.id(), modelSettingsService.getEmbeddingModel(user.id()));
    }

    /**
     * Answers 409 when the change would delete indexed chunks and {@code resetDocuments} is not set.
     */
    @PutMapping("/embeddings")
    public EmbeddingsModelRs updateEmbeddingsModel(@AuthenticationPrincipal AuthUser user,
                                                   @RequestBody EmbeddingsModelUpdateRq rq) {
        return toRs(user.id(), modelSettingsService.updateEmbeddingModel(user.id(),
                entity -> mapper.apply(entity, rq), rq.resetDocuments()));
    }

    @GetMapping("/summarizer")
    public SummarizerModelRs getSummarizerModel(@AuthenticationPrincipal AuthUser user) {
        return mapper.toRs(modelSettingsService.getSummarizerModel(user.id()));
    }

    @PutMapping("/summarizer")
    public SummarizerModelRs updateSummarizerModel(@AuthenticationPrincipal AuthUser user,
                                                   @RequestBody SummarizerModelUpdateRq rq) {
        return mapper.toRs(modelSettingsService.updateSummarizerModel(user.id(), entity -> mapper.apply(entity, rq)));
    }

    private EmbeddingsModelRs toRs(UUID userId, EmbeddingModelEntity entity) {
        return mapper.toRs(entity, modelSettingsService.countIndexedChunks(userId));
    }
}
