package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.settings.ChatModelUpsertRq;
import io.github.admiralxy.agent.controller.request.settings.EmbeddingsModelUpdateRq;
import io.github.admiralxy.agent.controller.request.settings.SummarizerModelUpdateRq;
import io.github.admiralxy.agent.controller.response.settings.ChatModelRs;
import io.github.admiralxy.agent.controller.response.settings.ChatModelsRs;
import io.github.admiralxy.agent.controller.response.settings.EmbeddingsModelRs;
import io.github.admiralxy.agent.controller.response.settings.SummarizerModelRs;
import io.github.admiralxy.agent.entity.ChatModelSettingsEntity;
import io.github.admiralxy.agent.entity.EmbeddingsModelSettingsEntity;
import io.github.admiralxy.agent.entity.SummarizerModelSettingsEntity;
import io.github.admiralxy.agent.service.model.ModelSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settings/models")
public class ModelSettingsController {

    public static final short SINGLETON_ID = 1;

    private final ModelSettingsService modelSettingsService;

    @GetMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify() {
        // do nothing
    }

    @GetMapping("/chat")
    public ChatModelsRs getChatModels() {
        return new ChatModelsRs(modelSettingsService.getChatModels().stream().map(this::toChatModelRs).toList());
    }

    @PostMapping("/chat")
    public ChatModelRs createChatModel(@RequestBody ChatModelUpsertRq rq) {
        ChatModelSettingsEntity entity = new ChatModelSettingsEntity();
        applyChatModel(entity, rq);
        return toChatModelRs(modelSettingsService.createChatModel(entity));
    }

    @PutMapping("/chat/{id}")
    public ChatModelRs updateChatModel(@PathVariable UUID id, @RequestBody ChatModelUpsertRq rq) {
        ChatModelSettingsEntity entity = new ChatModelSettingsEntity();
        applyChatModel(entity, rq);
        return toChatModelRs(modelSettingsService.updateChatModel(id, entity));
    }

    @DeleteMapping("/chat/{id}")
    public void deleteChatModel(@PathVariable UUID id) {
        modelSettingsService.deleteChatModel(id);
    }

    @GetMapping("/embeddings")
    public EmbeddingsModelRs getEmbeddingsModel() {
        return toEmbeddingsRs(modelSettingsService.getEmbeddingsModel());
    }

    @PutMapping("/embeddings")
    public EmbeddingsModelRs updateEmbeddingsModel(@RequestBody EmbeddingsModelUpdateRq rq) {
        EmbeddingsModelSettingsEntity entity = new EmbeddingsModelSettingsEntity();
        entity.setId(SINGLETON_ID);
        entity.setProvider(rq.provider());
        entity.setBaseUrl(rq.baseUrl());
        entity.setApiKey(rq.apiKey());
        entity.setName(rq.name());
        entity.setDimensions(rq.dimensions());
        entity.setMaxDocumentTokens(rq.maxDocumentTokens());

        return toEmbeddingsRs(modelSettingsService.updateEmbeddingsModel(entity));
    }

    @GetMapping("/summarizer")
    public SummarizerModelRs getSummarizerModel() {
        return toSummarizerRs(modelSettingsService.getSummarizerModel());
    }

    @PutMapping("/summarizer")
    public SummarizerModelRs updateSummarizerModel(@RequestBody SummarizerModelUpdateRq rq) {
        SummarizerModelSettingsEntity entity = new SummarizerModelSettingsEntity();
        entity.setId(SINGLETON_ID);
        entity.setProvider(rq.provider());
        entity.setName(rq.name());
        entity.setBaseUrl(rq.baseUrl());
        entity.setApiKey(rq.apiKey());
        entity.setSystemPrompt(rq.systemPrompt());

        return toSummarizerRs(modelSettingsService.updateSummarizerModel(entity));
    }

    private void applyChatModel(ChatModelSettingsEntity entity, ChatModelUpsertRq rq) {
        entity.setProvider(rq.provider());
        entity.setLabel(rq.label());
        entity.setName(rq.name());
        entity.setBaseUrl(rq.baseUrl());
        entity.setApiKey(rq.apiKey());
        entity.setStreaming(rq.streaming());
        entity.setSystemPrompt(rq.systemPrompt());
        entity.setPriority(rq.priority());
        entity.setTemperature(rq.temperature());
        entity.setMaxContextTokens(rq.maxContextTokens());
    }

    private ChatModelRs toChatModelRs(ChatModelSettingsEntity entity) {
        return new ChatModelRs(
                entity.getId(),
                entity.getProvider(),
                entity.getLabel(),
                entity.getName(),
                entity.getBaseUrl(),
                entity.getApiKey(),
                entity.isStreaming(),
                entity.getSystemPrompt(),
                entity.getPriority(),
                entity.getTemperature(),
                entity.getMaxContextTokens()
        );
    }

    private EmbeddingsModelRs toEmbeddingsRs(EmbeddingsModelSettingsEntity entity) {
        return new EmbeddingsModelRs(
                entity.getProvider(),
                entity.getBaseUrl(),
                entity.getApiKey(),
                entity.getName(),
                entity.getDimensions(),
                entity.getMaxDocumentTokens()
        );
    }

    private SummarizerModelRs toSummarizerRs(SummarizerModelSettingsEntity entity) {
        return new SummarizerModelRs(
                entity.getProvider(),
                entity.getName(),
                entity.getBaseUrl(),
                entity.getApiKey(),
                entity.getSystemPrompt()
        );
    }
}
